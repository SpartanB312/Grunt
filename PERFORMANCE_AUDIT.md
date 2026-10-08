# Аудит производительности Grunteon

> Первоначальный аудит базового коммита `830295b04c5f1565159fba33062fdc842d921e08`. Номера строк относятся к этому состоянию. Реализованные изменения и актуальные результаты проверки описаны в [PERFORMANCE_FIXES.md](<PERFORMANCE_FIXES.md>).

## Результат и границы проверки

Проверены основные runtime-пути ядра, SSA/Flow IR, native-генерации, GLSL/Yapyap, desktop UI, backend, index и bootstrap. Ниже — подтверждённые исходниками механизмы лишних затрат и конкретные варианты исправления. Их доля в реальной задержке зависит от конфигурации и входа: без representative workload и CPU/allocation profiles нельзя гарантировать обнаружение **всех** bottleneck’ов или обещать процент ускорения. Native и дополнительные шифрующие проходы могут быть выключены.

**P1** — исправлять в первую очередь при использовании соответствующего пути: сверхлинейная сложность, большой peak memory или потеря throughput. **P2** — следующий этап, выбирать по профилю. Приоритеты — инженерная оценка, не результат benchmark. Расходы обфускатора и сгенерированной программы намеренно разделены.

На момент первоначального аудита исходники и конфигурация не изменялись; был добавлен только этот отчёт. Локальный файл инструкций сохранён без изменений и не включается в PR. Сервисы тогда не поднимались, входные JAR не обфусцировались, dependency/toolchain versions не менялись.

## Первые изменения с наибольшим ожидаемым эффектом

1. №01: исправить maxStack-анализ Flow/native; есть точный API ASM без отключения верификации.
2. №02, №10–12, №15: убрать взрыв промежуточных иерархий/CFG, лишние graph scans и scratch locals.
3. №03–04: bounded I/O pipeline и metadata-only library loading с lazy full-body contract.
4. №17: не выполнять native lowering/emission повторно; №24–25: убрать log/registry нагрузку desktop UI.
5. Для backend отдельно №27–30: polling, восстановление queue consumers, общие process budgets и retention.

Не начинать с увеличения Xmx/числа потоков: это не исправляет квадратичные алгоритмы и может увеличить суммарный peak RSS.

## Подробные находки

### 01. Flow и native: квадратичная память ASM из-за завышенного maxStack — P1

**Код:** [JvmFlowImporter.kt:111–128](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/flow/jvm/JvmFlowImporter.kt#L111-L128>); [JvmFlowImporter.kt:457–460](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/flow/jvm/JvmFlowImporter.kt#L457-L460>); [NativeJvmCppMethodTranslator.kt:2188–2213](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeJvmCppMethodTranslator.kt#L2188-L2213>); [NativeJvmCppMethodTranslator.kt:2262–2266](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeJvmCppMethodTranslator.kt#L2262-L2266>).

**Проблема.** Перед Analyzer stack capacity устанавливается примерно равной числу инструкций N, независимо от реальной глубины S. ASM 9.10.1 сохраняет Frame на каждом достижимом instruction index и копирует весь массив locals+capacity: Θ(N·(L+N)) reference slots вместо зависимости от реального stack. При N=10 000 и малом L это около 100 млн слотов, то есть около 400 MB только под ссылки при compressed oops. Это расчёт, не замер RSS; параллельная обработка умножает peak heap. Flow также сохраняет завышение в metadata.

**Фикс.** Использовать тот же interpreter с Analyzer.analyzeAndComputeMaxs(owner, method): API включает динамическую ёмкость stack и вычисляет реальные maxs. Возвращать frames вместе с computed maxs; исходные поля MethodNode восстанавливать в finally. В Flow metadata сохранять вычисленный maxStack, а политику зарезервированных local slots учитывать отдельно. Не заменять на слепое доверие старому maxStack и не выключать проверку.

**Проверка и ограничения.** NOP/RETURN и shallow-stack методы 1k/2k/4k/8k инструкций; allocation/op, peak heap и время анализа. Обязательны stale maxStack, long/double, глубокий stack, exception handlers, Basic/Hierarchy, export+ASM+исполнение. Контракт зависимости проверен по официальным исходникам ASM, ссылки ниже.

### 02. ClassHierarchy: экспоненциальное накопление одинаковых предков — P1

**Код:** [ClassHierarchy.kt:377–431](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/hierarchy/ClassHierarchy.kt#L377-L431>).

**Проблема.** DFS делает addAll(ancestors[parent]) без дедупликации; unique применяется только после построения descendants. На повторяющихся interface diamonds размер промежуточных списков зависит от числа путей, а не уникальных предков. Затем дубликаты ещё раз размножаются в descendants. Даже после исправления полное транзитивное замыкание остаётся O(C²) в худшем случае.

**Фикс.** Объединять уникальные ancestor IDs при обработке каждого узла: primitive set/bitset либо отсортированные уникальные массивы. Descendants строить по уже уникальным предкам; по возможности не хранить одновременно ненужные массивы и hash sets. Сохранить стабильный порядок там, где он влияет на naming/RNG. Не обещать линейную память для полного замыкания.

**Проверка и ограничения.** Модель допустимой JVM interface-иерархии с 16 слоями/33 узлами дала 524 184 промежуточных ancestor entries против 512 уникальных; множества предков совпали на 4/8/12/16 слоях. Это проверка алгоритмической модели Python, не запуск Kotlin. Добавить реальный regression на diamonds и сравнение subtype/rename результатов.

### 03. JAR I/O: coroutine на каждый entry, без настоящего ограничения in-flight памяти — P1

**Код:** [WorkResources.kt:205–269](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/WorkResources.kt#L205-L269>); [JarDumper.kt:69–148](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/JarDumper.kt#L69-L148>).

**Проблема.** Чтение и dump запускают launch на каждый class/resource. Channel.BUFFERED ограничивает очередь готовых результатов, но не число producer-coroutines: заблокированный send удерживает уже созданный ClassNode/сжатый ByteArray. В худшем случае промежуточная память растёт с числом и размером всех entries, а не с числом CPU.

**Фикс.** Один producer descriptor-ов, ограниченный channel и фиксированные P workers; отдельно ограничить объём bytes in flight. Не ограничиваться Semaphore внутри тысяч уже созданных launch. Разделить blocking read и CPU parse/compress; map mutation/ZIP sink оставить последовательными. У исходных ClassNode всё равно есть неизбежная стоимость хранения входного проекта.

**Проверка и ограничения.** 10k/100k мелких classes, большие resources, медленный output. Измерять active tasks, retained ByteArray, heap/GC и throughput. Проверить cancellation/закрытие channels, повреждённые entries, исключения и одинаковое содержимое ZIP; сохранять контролируемую случайность.

### 04. Библиотеки загружаются с полными method bodies; отсутствующие классы ищутся повторно — P1/P2

**Код:** [WorkResources.kt:80–93](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/WorkResources.kt#L80-L93>); [WorkResources.kt:224–260](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/WorkResources.kt#L224-L260>); [ClassHierarchy.kt:129–169](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/hierarchy/ClassHierarchy.kt#L129-L169>); [JarDumper.kt:94–110](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/JarDumper.kt#L94-L110>).

**Проблема.** Для input и libraries используется EXPAND_FRAMES. Тела и frames всех библиотек занимают память, даже когда нужны только signatures/hierarchy. computeIfAbsent не запоминает null, поэтому один отсутствующий owner многократно вызывает ClassReader/исключение. checkMissing проходит все инструкции до launch каждого класса и выполняется даже при missingCheck=false.

**Фикс.** Разделить metadata-only library view (SKIP_CODE|SKIP_DEBUG|SKIP_FRAMES) и явную lazy загрузку bodies для потребителей, которым они нужны. Не выдавать phantom за полноценный bytecode; поддержка .gi сейчас не означает автоматическое чтение .gi в main. Ввести negative cache на стабильную resource generation, invalidation при добавлении класса. При missingCheck=false пропускать именно reference scan; hierarchy для COMPUTE_FRAMES сохранять. Проверять уникальных owners один раз на snapshot.

**Проверка и ограничения.** Малый input с большим classpath и повторными missing references. Считать parsed method bodies, lookup miss/exception count, load wall/heap. Проверить плагины, annotations/signatures, JDK lookup, generated classes и поведение fallback dump.

### 05. Повторные hierarchy snapshots и объединение всех collections — P2

**Код:** [WorkResources.kt:43–53](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/WorkResources.kt#L43-L53>); [ControlflowJump.kt:190–199](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/transformers/controlflow/ControlflowJump.kt#L190-L199>); [ControlflowFlattening.kt:192–206](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/transformers/controlflow/ControlflowFlattening.kt#L192-L206>); [JarDumper.kt:73–76](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/JarDumper.kt#L73-L76>).

**Проблема.** Каждый allClassCollection выделяет новую коллекцию; Flow-проходы и final dump независимо строят hierarchy, сортируют классы и материализуют транзитивное замыкание. Это повторение дорогой работы №02, а не самостоятельное доказательство её доминирования. Renamers используют другой набор входных классов, поэтому их snapshots нельзя безусловно считать одинаковыми.

**Фикс.** Job-scoped hierarchy/collection snapshot с ключом membership/name/super/interface revision и scope входа. Создавать/переиспользовать только на корректных barrier boundaries. Сначала централизовать изменения публичных maps/ClassNode или ограничить cache одной гарантированно неизменной фазой; вечный lazy-cache здесь неверен.

**Проверка и ограничения.** Считать builds и bytes на конфигурации с несколькими Flow/rename passes; тестировать MappingApplier, generated classes, изменённые super/interfaces и повторные transformers. Не добавлять/убирать barriers ради удобства cache.

### 06. LWWSP: дисбаланс тяжёлых классов и раннее прекращение stealing — P1/P2

**Код:** [LWWSP.kt:313–326](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/util/LWWSP.kt#L313-L326>); [LWWSP.kt:341–400](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/util/LWWSP.kt#L341-L400>); [PipelineBuilder.kt:231–259](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/PipelineBuilder.kt#L231-L259>).

**Проблема.** Работа делится по числу классов, не по стоимости; batch по умолчанию 32. Worker прекращает попытки stealing при remaining < 2·batchSize·workerCount, хотя у других могут оставаться тяжёлые классы. Например при 8 workers и batch=32 threshold равен 512. Уже исполняемый большой class batch неделим. После серии лёгких классов часть CPU может простаивать на дорогом хвосте.

**Фикс.** Статистика времени/размера классов, адаптивный небольшой batch для тяжёлых проходов, cost-aware выдача задач и stealing до отсутствия доступной работы. Исключить busy-spin, оставить worker-local scopes и корректное merge. Разделять класс на методы только для проходов с доказанной thread-safety, не для всего fused pipeline.

**Проверка и ограничения.** Skewed input: множество простых классов плюс несколько огромных; CPU utilization и p95/max worker duration при batch=1/4/16/32. Не выдавать уменьшение batch за гарантированное ускорение: для дешёвых passes scheduler overhead может вырасти.

### 07. Ресурсы и ZIP filesystems удерживаются дольше необходимого — P2

**Код:** [ResourceSet.kt:22–50](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/ResourceSet.kt#L22-L50>); [WorkResources.kt:115–124](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/WorkResources.kt#L115-L124>); [JarDumper.kt:79–90](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/JarDumper.kt#L79-L90>).

**Проблема.** ResourceSet.Single кеширует полные byte arrays без eviction. Чтение всех неизменяемых ресурсов во время dump может удержать их до конца job; при input-directory чтение class bytes тоже идёт через cache. ZIP filesystem открывается/переиспользуется без явного owner lifecycle и close, что особенно существенно для повторных desktop runs.

**Фикс.** Разделить mutable resource overlay и потоковое чтение неизменяемых файлов; не кешировать всё только ради output. Ввести AutoCloseable ownership/reference count для файловых систем и закрытие в finally на уровне job после последнего потребителя. Не закрывать чужую/разделяемую FS и не вытеснять изменённый resource без сохранения.

**Проверка и ограничения.** JAR с большими assets и 100 последовательных desktop runs: retained heap, открытые FD, возможность заменить входной архив после job, одинаковые resource hashes.

### 08. Сжатие: дорогой default и отдельный compressor на каждый entry — P2

**Код:** [ObfConfig.kt:110–112](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/ObfConfig.kt#L110-L112>); [JarDumper.kt:52–66](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/JarDumper.kt#L52-L66>); [WorkerJobRunner.kt:81–87](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/WorkerJobRunner.kt#L81-L87>).

**Проблема.** JAR default — BEST_COMPRESSION (9). Для каждого entry создаётся ZipOutputStream/Deflater и копируется ByteArrayOutputStream. Backend затем ещё раз DEFLATE-сжимает готовый output JAR в result ZIP. CPU benefit от изменения уровня зависит от данных; двойная упаковка сейчас потоковая, а не загрузка всего ZIP в heap.

**Фикс.** Сравнить уровни 1/6/9 и размер результата; добавить явный fast/balanced preset, не менять контракт default попутно. При необходимости reusable worker-local compression buffer/deflater с корректным reset/end. Для вложенного JAR использовать DEFLATED level=0; STORED потребует заранее CRC/size и может добавить проход. Сохранить ZIP headers/CRC/timestamps/corruption options.

**Проверка и ограничения.** CPU и wall отдельно на ASM dump, compression и result packaging; bytes read/written и итоговый size ratio, проверки unzip/class execution.

### 09. Renamers: broadcast полей, копии component arrays и попарные synthetic comparisons — P2

**Код:** [FieldHierarchy.kt:156–219](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/hierarchy/FieldHierarchy.kt#L156-L219>); [MethodHierarchy.kt:359–380](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/hierarchy/MethodHierarchy.kt#L359-L380>); [MethodRenamer.kt:264–297](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/transformers/rename/MethodRenamer.kt#L264-L297>).

**Проблема.** FieldHierarchy для каждого non-private field проходит descendants и кладёт новый пустой IntArraySet, хотя дальше использует keys. MethodHierarchy копирует одинаковый component array отдельно для каждого source: component размера k даёт Θ(k²) slots. Synthetic-группа одной signature попарно сравнивает k owners и пересечения descendants: O(k²·D) worst case.

**Фикс.** Для поля хранить primitive membership вместо пустых объектов, рассмотреть topological propagation. Для component создавать один immutable IntArray/EntryArray и делить ссылки. Для synthetic-group индексировать descendant→representative и union по пересечениям без всех пар; сохранить ancestor/self и diamond semantics.

**Проверка и ограничения.** Большое дерево с полями, component многих source methods, sibling interfaces с общим descendant. Сравнить mapping целиком, dispatch/bridge/private/static/field hiding и реальное исполнение; не переписывать уже существующий union-find на наивный поиск.

### 10. Flow/SSA import: линейное разрешение labels и block starts на каждом переходе — P1/P2

**Код:** [JvmFlowImporter.kt:334–340](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/flow/jvm/JvmFlowImporter.kt#L334-L340>); [JvmFlowImporter.kt:385–398](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/flow/jvm/JvmFlowImporter.kt#L385-L398>); [JvmSSAImporter.kt:786–799](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/jvm/JvmSSAImporter.kt#L786-L799>).

**Проблема.** instructions.indexOf(label), blockInfos.firstOrNull и поиск fallthrough повторяются на каждом target. Для J targets и T handlers стоимость O((J+T)·(N+B)+B²); плотные branches/switch дают квадратичный путь независимо от стоимости ASM.

**Фикс.** Import-local identity index instruction→position, массив nextExecutableIndex обратным проходом, start→BlockInfo и nextBlock. Handler interval искать по sorted starts плюс обход только покрытого диапазона.

**Проверка и ограничения.** Большие switch/цепочки/diamonds, consecutive labels/frames/line numbers, end sentinel=N, unreachable и exception regions. Сравнить CFG/metadata, затем roundtrip+ASM+execution.

### 11. Flow graph: полный scan edges на каждый block/port — P1

**Код:** [FlowGraph.kt:88–105](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/flow/core/FlowGraph.kt#L88-L105>); [FlowVerifier.kt:100–120](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/flow/core/FlowVerifier.kt#L100-L120>); [JvmFlowExporter.kt:101–135](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/flow/jvm/JvmFlowExporter.kt#L101-L135>); [JvmFlowExporter.kt:228–230](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/flow/jvm/JvmFlowExporter.kt#L228-L230>).

**Проблема.** outgoingEdges/incomingEdges фильтруют весь edges, edgeFrom делает firstOrNull. Verifier получает Θ(BE), exporter — до O(E²). Проверки ports сами добавляют Θ(P²) на большом switch из-за count/membership по спискам.

**Фикс.** Один immutable adjacency snapshot на verify/export: outgoing, incoming, (block,port)→edges. Counts/port sets собирать одним проходом. Не заменять duplicates на единственный элемент: verifier обязан видеть неправильный граф. Публичные mutable edges требуют локального snapshot либо полноценной invalidation.

**Проверка и ограничения.** Удвоение B/E на линейном CFG и P на switch должно давать почти линейное structural work; отдельно сравнить frames. Негативные тесты duplicate/missing/unknown ports и mutations между экспортами.

### 12. SSAVerifier: полные множества dominators и дорогой fixed point — P1

**Код:** [SSAVerifier.kt:416–455](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/core/SSAVerifier.kt#L416-L455>).

**Проблема.** Для каждого nonentry создаётся set всех blocks, затем многократные intersect/toMutableSet. Θ(B²) hash entries; reverse-layout chain требует Θ(B) раундов и может давать Θ(B³) копирований. verify каждый раз строит всё заново.

**Фикс.** Dense indices+bitsets, reverse-postorder/worklist, один predecessor index и reuse scratch intersections. Более крупный вариант — idom tree с tin/tout dominance queries. Не отключать dominance verification.

**Проверка и ограничения.** Natural/reverse/random layout одного CFG, loops/irreducible/unreachable SCC, exception edges. Сохранять includeExceptionEdges=true и явно определить policy недостижимых blocks до смены алгоритма. Метрики allocations и verify time отдельно от import.

### 13. Region planners пересканируют весь граф на каждый маленький регион — P2

**Код:** [SSARegion.kt:55–77](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/core/SSARegion.kt#L55-L77>); [SSARegion.kt:120–130](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/core/SSARegion.kt#L120-L130>); [FlowRegion.kt:47–61](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/flow/core/FlowRegion.kt#L47-L61>); [FlowRegion.kt:83–115](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/flow/core/FlowRegion.kt#L83-L115>).

**Проблема.** На каждом регионе несколько filters всего E; при minBlocks=2/preferSmall и R=Θ(B) получается Θ(R·E), хотя результат может быть O(E). Это остаётся проблемой и для SSA с уже существующим predecessor index.

**Фикс.** Общие outgoing/incoming и exception-membership индексы; internal/exit собирать из рёбер blocks региона, entry — из incoming entry. Сохранить исходный ordinal для стабильного порядка результатов.

**Проверка и ограничения.** Цепочки с регионами по два блока и ростом B, self-loops/handlers/options; сравнение region membership, edge lists и order.

### 14. Region SSA CFF: глобальный liveness и rewrite на каждый регион — P1/P2

**Код:** [SSARegionControlFlowFlattener.kt:71–95](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/transform/SSARegionControlFlowFlattener.kt#L71-L95>); [SSARegionControlFlowFlattener.kt:161–174](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/transform/SSARegionControlFlowFlattener.kt#L161-L174>); [SSARegionControlFlowFlattener.kt:285–315](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/transform/SSARegionControlFlowFlattener.kt#L285-L315>); [SSARegionControlFlowFlattener.kt:363–413](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/transform/SSARegionControlFlowFlattener.kt#L363-L413>).

**Проблема.** Каждый маленький регион вызывает collect definitions/live-ins всей уже растущей функции, fixed point и глобальные переписывания terminators. Даже без больших live sets нижняя граница Ω(R·(Nssa+E+B)). Проверка maxDispatcherArgs выполняется после дорогого анализа.

**Фикс.** Дешёвый заведомый rejection по original args до liveness. Def/use indices и dirty-predecessor worklist после изменения региона, переписывать только затронутые uses/edges. Не выносить один liveness snapshot наружу без invalidation: каждый rewrite меняет CFG и arguments.

**Проверка и ограничения.** Many-regions/transitive live-out/loops/handler boundaries; compare verify+export+execution и число visited blocks per region. Высокий семантический риск; делать после локальных, менее рискованных оптимизаций.

### 15. SSA exporter расходует новые scratch locals на каждое ребро — P1

**Код:** [JvmSSAExporter.kt:291–305](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/jvm/JvmSSAExporter.kt#L291-L305>); [JvmSSAExporter.kt:878–887](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/jvm/JvmSSAExporter.kt#L878-L887>).

**Проблема.** passArgs вызывает монотонный allocateTemp. maxLocals растёт как сумма widths аргументов всех edges, хотя scratch живёт только в одном transfer stub. Увеличиваются последующие ASM frames, WIDE bytecode и риск достижения classfile limits; особенно после CFF с большим carrier set.

**Фикс.** Отделить valueSlots high-water mark от scratchBase. Сбрасывать scratch cursor на каждом passArgs, хранить maxScratch по всем stubs. Сохранить двухфазную parallel copy load→temp→destination; последовательные прямые copies сломают swap/cycles.

**Проверка и ограничения.** Phi swap/cycle, long/double/ref, switches, constructors/uninitialized refs. Assert maxLocals зависит от максимальной ширины одного edge, а не их числа; ASM verification и исполнение.

### 16. Whole-function SSA CFF: квадратичный поиск carrier — P2

**Код:** [SSAControlFlowFlattener.kt:155–165](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/transform/SSAControlFlowFlattener.kt#L155-L165>); [SSARegionControlFlowFlattener.kt:197–197](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/transform/SSARegionControlFlowFlattener.kt#L197-L197>).

**Проблема.** Каждый из A block arguments ищется через carriers.first с начала A-элементного списка: Θ(A²) сравнений. Imported SSA часто содержит local-state arguments на множестве blocks.

**Фикс.** Один carrierByTargetArg map и getValue, как уже сделано в региональном варианте. Сохранить equality/duplicate policy и порядок назначения IDs. Это убирает поиск, но не неизбежный для текущего дизайна O(E·A) размер dense dispatcher arguments.

**Проверка и ограничения.** Blocks×args scaling, точное соответствие carriers и deterministic output, существующие cross-block-use tests.

### 17. Native: повторные translate/analysis при validation и split — P1/P2

**Код:** [NativeJvmCppMethodTranslator.kt:33–39](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeJvmCppMethodTranslator.kt#L33-L39>); [NativeCppBackend.kt:90–103](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeCppBackend.kt#L90-L103>); [NativeCppBackend.kt:259–273](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeCppBackend.kt#L259-L273>).

**Проблема.** validate вызывает полную translate и выбрасывает текст. Генерация выполняет её снова. При split сначала создаётся полный singleSource, затем те же методы снова генерируются по chunks: на обычном FullJvm пути два анализа/emits, при split — три, плюс удержание monolithic text.

**Фикс.** Validated lowering plan с анализами/labels, один emit method body, reuse в chunks. Runtime/header/registration генерировать отдельно. Учесть diagnostic sourceText, reference slots, intrinsic stats/config и commitKind; сохранить validate→generate→compile→commit и неизменные JVM bodies при compile failure.

**Проверка и ограничения.** 16/17/64/256 методов, split on/off; instrument translate/analyze counts, generation CPU/heap. Сравнить emitted source semantics, bindings/stats и native E2E, не только успешный C++ build.

### 18. Native output: JNI на каждый элемент primitive array — P1 при hot loops

**Код:** [NativeJvmCppMethodTranslator.kt:1550–1574](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeJvmCppMethodTranslator.kt#L1550-L1574>); [NativeJvmCppMethodTranslator.kt:1602–1626](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeJvmCppMethodTranslator.kt#L1602-L1626>); [NativeJvmCppMethodTranslator.kt:1264–1282](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeJvmCppMethodTranslator.kt#L1264-L1282>).

**Проблема.** FullJvm lowering испускает Get/Set*ArrayRegion(index,1) на каждую array instruction и проверки исключения. В sum/copy/image/crypto loops эта granularity даёт много JNI transitions. Это bottleneck сгенерированной программы, а не только обфускатора.

**Фикс.** Специализированное loop lowering с batched Region либо scoped Elements и cleanup на всех exits. Практичный вариант — фильтровать наиболее горячие loops из native. Не держать PrimitiveArrayCritical через произвольные JNI calls/долгую работу; не менять exception ordering/alias visibility ради скорости.

**Проверка и ограничения.** Array sum/copy 32..1M элементов: JNI calls/op, throughput, GC; null/OOB/alias/exception семантика. Сравнивать JVM baseline, cold native и warm native отдельно.

### 19. Native output: линейный vector bookkeeping local references — P1/P2 при reference-heavy native

**Код:** [NativeCppBackend.kt:703–718](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeCppBackend.kt#L703-L718>); [NativeJvmCppMethodTranslator.kt:295–307](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeJvmCppMethodTranslator.kt#L295-L307>).

**Проблема.** track_ref делает GetObjectRefType и линейный dedup vector. R разных handles в одном большом блоке дают Θ(R²) сравнений; forget_ref сканирует и erase сдвигает хвост. Cleanup на target повторяет forget_ref по живым locals/stack. Обычный loop очищается на targets: это не доказанная бесконечная утечка.

**Фикс.** Ownership-aware O(1) bookkeeping или compile-time last-use; batch sweep live handles вместо многократных erase. jobject handle identity не равна Java object identity; простой PopLocalFrame не эквивалентен существующему ownership.

**Проверка и ограничения.** Unrolled blocks с 32/128/512/2048 references и loops с live aliases, returned refs/pending exceptions; -Xcheck:jni, identity tests, JNI calls и comparisons/op.

### 20. GLSL: повторные token/call scans и повторный parse всех документов — P1/P2

**Код:** [GlslAnalysis.kt:150–154](<grunt-glsl/src/main/kotlin/net/spartanb312/grunt/glsl/shader/GlslAnalysis.kt#L150-L154>); [GlslParser.kt:206–209](<grunt-glsl/src/main/kotlin/net/spartanb312/grunt/glsl/shader/GlslParser.kt#L206-L209>); [GlslInlinePass.kt:31–69](<grunt-glsl/src/main/kotlin/net/spartanb312/grunt/glsl/shader/GlslInlinePass.kt#L31-L69>); [GlslProcessor.kt:12–55](<grunt-glsl/src/main/kotlin/net/spartanb312/grunt/glsl/shader/GlslProcessor.kt#L12-L55>).

**Проблема.** bodyTokens фильтрует все significantTokens на каждую функцию, причём дважды через analysis/statements: O(F·T). Inline candidates пересканируют calls/statements, overlap patches.none даёт O(P²). До восьми inline iterations повторно parseAll, затем ещё parseAll для rename.

**Фикс.** Function token ranges/subList, call-name→sites→statement index, ordered interval index для accepted patches. Cache parsed document/analysis по source revision и dependency invalidation; reuse последнего неизменённого parse перед rename. Lexer и PatchWriter сами по себе здесь не названы квадратичными.

**Проверка и ограничения.** F/functions, C/calls, S/statements и P/patches scaling; lex/parse/analyze counts и allocation bytes. Сохранить scopes/overloads/directives, cross-file links, offsets после edits, порядок RNG/naming.

### 21. GLSL: inline expansion budget обходится при удалении функции — P1/P2

**Код:** [GlslInlinePass.kt:57–63](<grunt-glsl/src/main/kotlin/net/spartanb312/grunt/glsl/shader/GlslInlinePass.kt#L57-L63>); [GlslInlinePass.kt:210–215](<grunt-glsl/src/main/kotlin/net/spartanb312/grunt/glsl/shader/GlslInlinePass.kt#L210-L215>); [GlslObfuscator.kt:65–74](<grunt-glsl/src/main/kotlin/net/spartanb312/grunt/glsl/transformers/GlslObfuscator.kt#L65-L74>).

**Проблема.** Полностью inlined helper добавляет patch с replacement="". Проверка expansion budget при любом таком deletion сразу возвращает true. Удаление одного определения не гарантирует компенсации его размножения в callers; растёт source и стоимость следующих parse/driver compile. GPU slowdown без runtime measurement не доказан.

**Фикс.** Считать net growth по фактически принятым непересекающимся patches, включая экономию deletion, но без blanket bypass. Проверить смысл maxExpansionRatio и поведение при overlap до изменения.

**Проверка и ограничения.** Helper из семи statements+return с 16 поддерживаемыми calls: assert итоговый размер/ratio и время последующих passes; correctness shader compilation.

### 22. Yapyap SPECK: отдельный большой helper на каждое вхождение literal — P1/P2 при включённом проходе

**Код:** [NumberSPECKEncrypt.kt:127–140](<grunt-yapyap/src/main/kotlin/net/spartanb312/grunt/yapyap/transformers/encrypt/number/NumberSPECKEncrypt.kt#L127-L140>); [NumberSPECKEncrypt.kt:302–357](<grunt-yapyap/src/main/kotlin/net/spartanb312/grunt/yapyap/transformers/encrypt/number/NumberSPECKEncrypt.kt#L302-L357>).

**Проблема.** На каждое выбранное occurrence создаётся method с 22/27 развёрнутыми rounds. Для SPECK32 только rounds дают 22×32=704 instructions плюс обвязка. Большие helper counts увеличивают следующие passes/dump/JAR/classloading/JIT. Лимит caller instructions не ограничивает суммарный размер companions.

**Фикс.** Helper/classfile budgets, opt-in reuse по type+raw literal bits либо shared decryptor/pool; split companions. Это меняет разнообразие защиты и потребление seed, поэтому оформлять отдельным режимом, не скрытой заменой. Не утверждать неизбежные rounds на каждом warm load: JIT может constant-fold.

**Проверка и ограничения.** 1k/10k повторяющихся и разных literals: methods, JAR bytes, generation/classload/JIT, cold/warm; NaN payloads и ±0 raw-bit roundtrip.

### 23. Yapyap ABE: дорогое первое обращение к tiny pools — P2, цена защиты

**Код:** [StringAttributeBasedEncrypt.kt:69–83](<grunt-yapyap/src/main/kotlin/net/spartanb312/grunt/yapyap/transformers/encrypt/string/StringAttributeBasedEncrypt.kt#L69-L83>); [StringAttributeBasedEncrypt.kt:209–223](<grunt-yapyap/src/main/kotlin/net/spartanb312/grunt/yapyap/transformers/encrypt/string/StringAttributeBasedEncrypt.kt#L209-L223>); [StringAbeRuntime.java:239–259](<grunt-yapyap/src/main/java/net/spartanb312/grunt/yapyap/runtime/StringAbeRuntime.java#L239-L259>); [NumberAbeRuntime.java:251–271](<grunt-yapyap/src/main/java/net/spartanb312/grunt/yapyap/runtime/NumberAbeRuntime.java#L251-L271>).

**Проблема.** При default minPoolSize=2 для строк и 4 для чисел создаются маленькие owner/type pools. Для пяти attributes дешифровка pool при инициализации требует 2·5+1=11 pairings; строковый и числовой pool могут дать до 22 на owner плюс setup/AES. Warm доступ — обычный array load; генерация curve params уже общая на transformer.

**Фикс.** Использовать minPoolSize/filters для tiny/startup pools, исследовать bounded cache immutable decoded parameters/precomputations. Не снижать curve sizes и не объединять CP-ABE policies/pool kinds как якобы бесплатную оптимизацию. Учитывать JPBC thread-safety и classloader lifecycle.

**Проверка и ограничения.** 10/100/1000 классов×2/512 literals, first access отдельно от warm; PBC present/absent, startup p95, allocations, init ordering/failure semantics.

### 24. UI logs: неограниченное хранение, eager rendering и callback на строку — P1 для desktop

**Код:** [App.kt:97–103](<grunt-ui/src/main/kotlin/net/spartanb312/grunteon/ui/App.kt#L97-L103>); [ObfuscationPage.kt:34–36](<grunt-ui/src/main/kotlin/net/spartanb312/grunteon/ui/ObfuscationPage.kt#L34-L36>); [ObfuscationPage.kt:53–69](<grunt-ui/src/main/kotlin/net/spartanb312/grunteon/ui/ObfuscationPage.kt#L53-L69>).

**Проблема.** mutableStateList хранит весь лог, каждая строка ставит invokeLater, Column создаёт Text для всех строк. O(L) retention/узлов, много EDT callbacks и повторный auto-scroll; совокупный обход может быть квадратичным при частых отдельных commits, но Compose коалесцирует часть invalidations.

**Фикс.** Bounded visible tail/ring buffer, LazyColumn со стабильным sequence ID, batch drain на EDT раз в кадр/50–100 ms, sticky-bottom auto-scroll. Полный лог писать отдельным потоковым sink: ограничение UI не должно терять единственную полную копию.

**Проверка и ограничения.** 1k/10k/100k строк при одинаковом rate; p95/p99 EDT lag/frame time, heap/callback count. Engine уже выполняется в отдельном Thread — переносить его с EDT повторно не требуется.

### 25. UI: registry и reflection metadata пересобираются при редактировании — P2

**Код:** [PipelineEditorPage.kt:251–252](<grunt-ui/src/main/kotlin/net/spartanb312/grunteon/ui/PipelineEditorPage.kt#L251-L252>); [TransformerUiUtils.kt:7–10](<grunt-ui/src/main/kotlin/net/spartanb312/grunteon/ui/TransformerUiUtils.kt#L7-L10>); [TransformerUiUtils.kt:33–51](<grunt-ui/src/main/kotlin/net/spartanb312/grunteon/ui/TransformerUiUtils.kt#L33-L51>); [EditorPanels.kt:287–295](<grunt-ui/src/main/kotlin/net/spartanb312/grunteon/ui/EditorPanels.kt#L287-L295>); [Configs.kt:73–99](<grunt-ui/src/main/kotlin/net/spartanb312/grunteon/ui/Configs.kt#L73-L99>).

**Проблема.** definitions getter целиком map/sort registry, а вызывается даже внутри indexOfLast predicate. Для N entries и D definitions худший случай O(N·D log D) на изменение. ConfigEditor повторяет reflection discovery/filter/sort. Изменение значений config инвалидирует расчёты всего списка.

**Фикс.** Immutable schema/index по config KClass после registry freeze; localized view по language revision. Захватывать definitions вне predicates, findDefinition делать map lookup. Memoize order/mapping по корректным зависимостям rules, а не только удобным полям. Не кешировать mutable values/callbacks вместе со schema и не менять порядок/повторы pipeline.

**Проверка и ограничения.** Typing/drag на 10/100/500 entries, counter transformerDefinitions, JFR allocation и latency; language switch, enabled/order warnings, plugin catalogue. Поиск тоже должен инвалидироваться при смене языка/definitions.

### 26. UI config I/O остаётся на UI dispatcher — P2

**Код:** [Models.kt:124–140](<grunt-ui/src/main/kotlin/net/spartanb312/grunteon/ui/Models.kt#L124-L140>); [Models.kt:241–303](<grunt-ui/src/main/kotlin/net/spartanb312/grunteon/ui/Models.kt#L241-L303>); [ToolbarSettings.kt:61–78](<grunt-ui/src/main/kotlin/net/spartanb312/grunteon/ui/ToolbarSettings.kt#L61-L78>); [ObfConfig.kt:30–36](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/ObfConfig.kt#L30-L36>).

**Проблема.** load/save используют синхронные readText/JSON/writeText. launch/async того же rememberCoroutineScope и debounce delay не переводят работу на IO. Большой JSON или медленный home/config path блокирует UI и startup.

**Фикс.** Immutable snapshot+revision на EDT, serialize/read/write на IO/выделенном serialized writer; применять loaded state на EDT только при совпадении revision/path. Temp+atomic replacement, flush при exit, защита от позднего load/save и dirty-state races.

**Проверка и ограничения.** Startup-to-first-frame, open/save p95 pause, FileRead/FileWrite threads; редактирование во время save и exit. Не читать/менять Compose mutable state с IO thread.

### 27. Web polling всех jobs, включая завершённые, с перекрытием циклов — P1/P2 для backend

**Код:** [app.js:32–34](<grunt-backend/src/main/resources/static/app.js#L32-L34>); [app.js:110–160](<grunt-backend/src/main/resources/static/app.js#L110-L160>); [JobService.kt:65–68](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/JobService.kt#L65-L68>); [RedisJobRepository.kt:18–21](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/RedisJobRepository.kt#L18-L21>).

**Проблема.** Каждые 2 s последовательно fetch всех jobs без single-flight. Если цикл дольше интервала, новые циклы наслаиваются. SUCCESS/FAILED опрашиваются бесконечно; каждый status вызывает HGETALL+parse и filesystem exists, затем полную DOM rebuild. Для 30 jobs при быстрых ответах — примерно 15 req/s на вкладку даже после завершения; это расчёт, не нагрузочный тест. Срез localStorage не ограничивает live Map.

**Фикс.** Polling только nonterminal, recursive timeout после завершения, AbortController/backoff/jitter/visibility pause, capped concurrency или batch status API. Terminal refresh вручную/по retention events. Availability хранить после atomic artifact publish, но download/cleanup должны проверять действительное наличие результата.

**Проверка и ограничения.** 1/30/100 jobs и 1/100 clients, медленный API/404/503: inflight, req/s, Redis commands/s, FS stats/s, JS long tasks, p95 latency.

### 28. Redis-сбой может навсегда уменьшить число dispatch loops — P1 для надёжного throughput

**Код:** [JobDispatcher.kt:23–31](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/JobDispatcher.kt#L23-L31>); [JobDispatcher.kt:40–64](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/JobDispatcher.kt#L40-L64>); [RedisJobQueue.kt:12–18](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/RedisJobQueue.kt#L12-L18>).

**Проблема.** Ровно C tasks исполняют dispatchLoop. Catch покрывает poll, но repository.find/status updates могут завершить submitted Runnable; supervisor его не перезапускает. Очередь уже leftPop-нула job, claim/ack отсутствует. Это не обычный CPU hotspot, а падение service capacity C→C−1 и возможное зависание jobs после сбоя.

**Фикс.** Supervision всей итерации, backoff/metrics с сохранением interruption; reliable claim (processing list/lease либо Streams consumer groups), ack после terminal commit, reconciliation с fencing/idempotency. Create metadata+enqueue также согласовать. Blind reenqueue недопустим: предыдущая JVM может ещё писать тот же job directory.

**Проверка и ограничения.** Fault injection на find/RUNNING/final update, остановка backend после claim; live-loop gauge, oldest queue age, восстановление C consumers и один активный artifact writer на job. Redis poll уже blocking, не busy-loop.

### 29. Worker JVM конкурируют за все CPU; lifecycle может оставлять процессы — P1/P2 для backend

**Код:** [application.yml:19–22](<grunt-backend/src/main/resources/application.yml#L19-L22>); [PipelineBuilder.kt:214–216](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/PipelineBuilder.kt#L214-L216>); [WorkerProcessRunner.kt:14–28](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/WorkerProcessRunner.kt#L14-L28>); [JobDispatcher.kt:34–37](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/JobDispatcher.kt#L34-L37>).

**Проблема.** По умолчанию две JVM с -Xmx4G и каждая создаёт engine pool availableProcessors(). Это около C·P engine threads плюс GC/coroutines/native. 8 GiB — сумма max heap, не выделенный RSS и не total-memory limit. Timeout=0 не ограничивает job. destroyForcibly без wait/reap и interruption без finally cleanup могут оставлять child за пределами ожидаемого concurrency.

**Фикс.** Общий CPU/RAM admission budget, per-child heap и -XX:ActiveProcessorCount либо явный engine parallelism, отдельный native compiler budget. Terminate/reap в finally, при необходимости process-tree cleanup; не освобождать slot до termination. Оставить process isolation из-за Logger/registry; worker-ветка сейчас не поднимает Spring.

**Проверка и ограничения.** Concurrency sweep 1/2/...: jobs/min вместе с queue-wait/service p95, суммарный RSS/GC/CPU/context switches. Fault tests timeout/cancel/native descendants и short-job spawn cost.

### 30. Нет встроенных admission bounds и retention — P1/P2 для долгоживущего backend

**Код:** [JobService.kt:24–60](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/JobService.kt#L24-L60>); [RedisJobRepository.kt:14–16](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/RedisJobRepository.kt#L14-L16>); [WorkerJobRunner.kt:20–26](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/WorkerJobRunner.kt#L20-L26>); [WorkerJobRunner.kt:66–67](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/WorkerJobRunner.kt#L66-L67>).

**Проблема.** Submissions сохраняются на диск и в Redis без queue/disk quota; metadata не имеет TTL, завершённые каталоги/ZIP не очищаются этим backend. При λ>μ backlog растёт как (λ−μ)t; даже без overload накопление completed jobs растёт с их общим числом, если нет внешнего janitor.

**Фикс.** Атомарные pending/running и byte quotas, 429/503+Retry-After, cleanup staged uploads при ошибке. Configurable terminal retention с FS+metadata lifecycle, lease/download guards и grace period. Не удалять active/download jobs. Для нескольких backend hosts локальный absolute job path потребует shared storage/ownership.

**Проверка и ограничения.** Sustained overload/large libraries/failed uploads: queue depth/age, bytes/job, disk high-water, Redis memory, recovery. Фактические multipart limits проверить отдельно: в YAML они расположены под server.servlet, а стандартный Boot namespace — spring.servlet.multipart; объявленные 512MB/2048MB нельзя считать проверенными effective limits.

### 31. Логирование синхронно flush-ит каждую строку и дублирует worker output — P2

**Код:** [SimpleLogger.kt:51–59](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/util/logging/SimpleLogger.kt#L51-L59>); [WorkerJobRunner.kt:54–67](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/WorkerJobRunner.kt#L54-L67>); [WorkerProcessRunner.kt:18–20](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/WorkerProcessRunner.kt#L18-L20>).

**Проблема.** Каждая строка форматируется, печатается и flush-ится в файл. Worker stdout дополнительно перенаправляется в другой файл: два disk sinks для одних сообщений. flush здесь не fsync, но он убирает buffering и может тормозить producer threads на log-heavy задачах.

**Фикс.** Один bounded ordered async/file sink, batching по bytes/time, заданная backpressure/drop policy. Drain/flush/close до упаковки result ZIP; stdout оставить для bootstrap/crash либо понизить verbosity. Переиспользовать thread-safe DateTimeFormatter вместо SimpleDateFormat на строку как вторичную оптимизацию.

**Проверка и ограничения.** Log rate/bytes, FileWrite/monitor wait, elapsed job на local/slow storage; полный tail лога в result ZIP и порядок сообщений при параллельных producers.

### 32. Bootstrap и index: лишний архивный I/O, lifetime и DOM JSON — P2

**Код:** [ExternalClassLoader.java:189–219](<grunt-bootstrap/src/main/java/net/spartanb312/everett/bootstrap/ExternalClassLoader.java#L189-L219>); [Main.kt:21–38](<grunt-index/src/main/kotlin/net/spartanb312/grunteon/index/Main.kt#L21-L38>); [Read.kt:13–30](<grunt-index/src/main/kotlin/net/spartanb312/grunteon/index/io/Read.kt#L13-L30>); [Save.kt:48–62](<grunt-index/src/main/kotlin/net/spartanb312/grunteon/index/io/Save.kt#L48-L62>).

**Проблема.** Plugin loader индексирует archive через ZipInputStream: переход к следующему entry требует потребить/распаковать предыдущий, хотя нужны только имена. Потоки/архивы не закрываются явно. Index тоже запускает coroutine на class и не закрывает JarFile/input streams через use. .gi writer строит весь JsonObject и полную pretty JSON строку; reader создаёт DOM плюс ClassInfo graph.

**Фикс.** Для списка entries использовать JarFile/ZipFile central directory и try-with-resources/use; безопасно закрывать class URL streams. Для index — bounded workers, streaming JsonReader/JsonWriter, сохранение version/JSON semantics. Не менять формат .gi или classloader delegation как побочный эффект оптимизации.

**Проверка и ограничения.** Большой plugin JAR, многократные открытия, большой .gi: FD/bytes inflated/peak heap и startup. Class/resource lookup и index roundtrip/version validation; bootstrap не является главным CLI, поэтому при отсутствии plugins вклад может быть мал.

## Дополнительные локальные кандидаты

Эти места подтверждены кодом, но без профиля не стоит ставить их выше основных проблем.

- **ReferenceObfuscate.** [ReferenceObfuscate.kt:319–403](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/transformers/other/ReferenceObfuscate.kt#L319-L403>) и [ReferenceObfuscate.kt:449–459](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/transformers/other/ReferenceObfuscate.kt#L449-L459>): indexOf и построение полного списка anchors повторяются для каждого GOTO; bridge plans строятся для всех candidates до проверки максимального числа вставок. Создать index/eligible anchors один раз, планировать только необходимое число валидных bridges, сохранив RNG-consumption contract либо явно версионировав его. Мерить bootstrap-generation на длинных helpers; не называть это основным ASM пути всего проекта.
- **MethodInliner.** [MethodInliner.kt:180–194](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/transformers/optimize/MethodInliner.kt#L180-L194>) и [MethodInliner.kt:237–257](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/transformers/optimize/MethodInliner.kt#L237-L257>): local-slot sizes и argument types пересчитываются на каждое inline occurrence. Хранить в immutable InlineTarget snapshot с учётом того, что исходный MethodNode может измениться по ходу прохода; не кешировать устаревший mutable target.
- **SSA exception export.** [JvmSSAExporter.kt:493–506](<grunt-ir/src/main/kotlin/net/spartanb312/grunt/ir/ssa/jvm/JvmSSAExporter.kt#L493-L506>): sortedBy с function.blocks.indexOf делает линейный lookup на сравнениях. Один orderIndex и min/max по защищённым blocks уберут лишнюю сортировку/поиск. Сохранить текущую interval coverage; исправление noncontiguous semantics — отдельная задача.
- **Redis status updates.** [RedisJobRepository.kt:24–33](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/RedisJobRepository.kt#L24-L33>): три последовательные команды на transition. Atomic Lua/multi-field update уменьшит RTT и смешанные snapshots; эффект заметнее на remote Redis/коротких jobs, чем на двух долгих CPU workers.
- **Config upload.** [JobService.kt:78–90](<grunt-backend/src/main/kotlin/net/spartanb312/grunteon/backend/JobService.kt#L78-L90>): MultipartFile.bytes и copyOfRange дают две heap copies. Малый отдельный config limit и streaming BOM removal; это не относится к input/libs transferTo или потоковому result download.

- **Native incremental compile.** [NativeCompiler.kt:488–519](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeCompiler.kt#L488-L519>) и [633–649](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeCompiler.kt#L633-L649>): sources безусловно переписываются, GNU-like split заново вызывает compiler для каждого translation unit. Собственный content-addressed object cache по source+headers+toolchain/version+target+flags сократит повторные builds. Внешний compiler wrapper/Zig может уже иметь cache — эффект проверять отдельно. Atomic manifest, отсутствие commit после compile failure и cold/identical/one-method-change benchmark обязательны.
- **Native weak-reference caches.** [NativeCppBackend.kt:729–774](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeCppBackend.kt#L729-L774>): method/field slot vectors пропускают dead weak refs, но не удаляют их. При длительном classloader churn lookup под mutex растёт с историей, накапливаются weak handles. Prune с DeleteWeakGlobalRef и безопасной identity/liveness проверкой; benchmark repeated load/unload/GC и multithread lookup. Не превращать weak cache в strong cache, удерживающий classloaders.
- **ABE no-op setup.** [StringAttributeBasedEncrypt.kt:117–140](<grunt-yapyap/src/main/kotlin/net/spartanb312/grunt/yapyap/transformers/encrypt/string/StringAttributeBasedEncrypt.kt#L117-L140>) и [NumberAttributeBasedEncrypt.kt:130–153](<grunt-yapyap/src/main/kotlin/net/spartanb312/grunt/yapyap/transformers/encrypt/number/NumberAttributeBasedEncrypt.kt#L130-L153>): shared curve parameters создаются до фильтров, даже при нуле подходящих pools. Thread-safe lazy once-per-pass setup по первому принятому pool уберёт no-op cost; не делить mutable Pairing между workers. Проверить нулевой/один/много pools, RNG и ошибку инициализации.

## Что не следует «оптимизировать» слепо

- Не отключать Flow/SSA/ASM проверки, не включать forceComputeMax глобально ради выигрыша: успешный dump не доказывает корректный bytecode, а fallback может скрыть проблему.
- Не удалять barriers и не сортировать/дедуплицировать список transformers. Fused passes имеют особый межклассовый контракт.
- Не переносить backend jobs в общий in-process pool без отдельного redesign global Logger/registry.
- Не вводить вечные graph/liveness caches поверх публичных mutable maps/edges/MethodNode.
- Не снижать криптографические параметры и не убирать защиту горячего кода без явного решения о tradeoff.
- Не менять seed derivation на общий RNG/порядок потоков.
- Не считать каждый toArray/filter ошибкой: snapshot часто нужен для корректного ASM mutation.
- Сохранять лицензии: nativecode и Yapyap отдельно лицензированы PolyForm Strict; перенос кода между областями не предлагается.

## Как измерять эффект

### Набор нагрузок

| Нагрузка | Что изолирует | Основные метрики |
|---|---|---|
| Малый input + большой classpath | №04–05 | load wall/CPU, bodies parsed, heap |
| 1k/2k/4k/8k shallow-stack instructions | №01 | allocated bytes, retained heap, analyze time |
| Interface diamonds, deep hierarchy | №02, №09 | unique/intermediate pairs, build time/heap |
| Branch/switch CFG, reverse-layout SSA | №10–16 | edges/blocks/rounds visited, alloc/op |
| 10k/100k classes + большие assets | №03, №07–08 | tasks in flight, ByteArray heap, ZIP CPU/size |
| Skewed class costs | №06 | worker time spread, CPU utilization |
| Native 16/17/64/256 methods | №17 | translate/analyze counts, source bytes |
| Native arrays/reference-heavy blocks | №18–19 | JNI calls/op, cold/warm throughput |
| Большие GLSL modules/helper fanout | №20–21 | parse count, source growth, CPU/alloc |
| Repeated/distinct literals и tiny pools | №22–23 | output size, classload, JIT, first access |
| 100k UI logs и 500 pipeline entries | №24–26 | p95/p99 EDT lag, heap, recompositions |
| Backend concurrency + fault/overload tests | №27–31 | jobs/min, queue wait, RSS, API/Redis req/s |

Для baseline и каждого patch фиксировать input/config/seed/JDK/CPU/heap, отключённые и включённые passes. Не смешивать холодный CLI запуск и warmed engine benchmark. Повторить несколько одинаковых запусков, показывать median/p95 и разброс, CPU/wall, allocated bytes/GC, peak heap/RSS, output size. Проценты считать лишь после этого. Для обычных запусков достаточно JFR; алгоритмические kernels затем выделить в JMH с несколькими forks.

### Инструментация

Текущий CLI [Main.kt:44–53](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/Main.kt#L44-L53>) измеряет только instance.run(); загрузка plugins/config и Grunteon.create (включая чтение JAR) находятся **вне** таймера. [ObfConfig.kt:124–129](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/ObfConfig.kt#L124-L129>) содержит deprecated profiler flag; активной wiring этого флага в просмотренном main-коде не найдено. Нужны phase timers/JFR events: resource read, hierarchy, fused flush, ASM import/verify/export, native validate/generate/compile, dump/compress, result pack. В fused pipeline измерять время callback pass, а не расстояние между pre/post: pre не барьер, post отложен.

После установки требуемых toolchains и сборки runnable UI JAR пример Linux-профиля (UBER_JAR и CONFIG — ваши абсолютные пути, output должен быть тестовым):

```bash
export UBER_JAR=/ABSOLUTE/PATH/Grunteon-3.0.0-all.jar
export CONFIG=/ABSOLUTE/PATH/perf-config.json
mkdir -p work/perf
java -XX:StartFlightRecording=filename=work/perf/baseline.jfr,settings=profile,dumponexit=true \
  -Xlog:gc*:file=work/perf/gc.log \
  -cp "$UBER_JAR" net.spartanb312.grunteon.obfuscator.MainKt \
  --config "$CONFIG"
```

Это предложенная команда, не выполненный профиль. Thin main JAR не является самостоятельным java -jar приложением. Для native runtime профилировать отдельно обработанную программу; её hot paths не совпадают с генератором. Backend запускать лишь на отдельном нагрузочном стенде.

## Проверки, фактически выполненные при аудите

- `pwd`, `git status --short`, `git diff --stat`, `git diff --check`: успешно; до добавления отчёта единственным untracked файлом был локальный файл инструкций. Чужие изменения не удалялись.
- `java -version`: OpenJDK 25.0.4.1; `bash gradlew --version`: Gradle 9.5.0, exit 0.
- `bash gradlew :grunt-main:compileKotlin --offline --max-workers=2 --console=plain`: **exit 1 до компиляции исходников**. Gradle не нашёл Java installation languageVersion=8 для [build.gradle.kts:21–23](<grunt-index/build.gradle.kts#L21-L23>); toolchain download repositories не настроены. Это ограничение среды, не обнаруженная ошибка Kotlin-кода. Зависимости не обновлялись и JDK не устанавливался.
- Проверка Python-модели concatenated ancestors против раннего set union: множества совпали на 4/8/12/16 слоях. Проверялся алгоритм, не реальные runtime performance или все hierarchy contracts.
- Проверены официальные исходники ASM 9.10.1: [Frame.java](<https://gitlab.ow2.org/asm/asm/-/raw/ASM_9_10_1/asm-analysis/src/main/java/org/objectweb/asm/tree/analysis/Frame.java>) (constructor/copy/init) и [Analyzer.java](<https://gitlab.ow2.org/asm/asm/-/raw/ASM_9_10_1/asm-analysis/src/main/java/org/objectweb/asm/tree/analysis/Analyzer.java>) (analyzeAndComputeMaxs, initial frame и сохранение frame по instruction index), оба HTTP 200.
- JMH/JFR, full suites, native E2E, GUI и backend load tests **не выполнялись**. Нет замеренных коэффициентов ускорения. После фиксов обязательны targeted tests, ASM analysis и реальное исполнение output, а не только diff/build. Полный main suite дополнительно требует локальных acceptance fixtures, отсутствующих в clean checkout.

## Предлагаемое разбиение работ

1. Отдельный небольшой PR для №01 с memory-scaling и stale-maxs regression tests.
2. PR для hierarchy dedup №02 и immutable per-operation Flow indices №10–11; сохранить порядок, ошибки и mapping.
3. PR для SSA scratch/lookup №15–16, затем отдельно dominance/region liveness №12–14 с расширенным semantic suite.
4. Bounded resource processing №03, затем library representation/lifecycle №04–07.
5. Native emit reuse №17 и GLSL budget/indexing №20–21 — независимые PR. JNI/криптографические tradeoffs №18–19/22–23 только с runtime benchmarks.
6. UI fixes №24–26 и backend reliability/budget №27–31 независимо от compiler-core; не ждать большого IR redesign.

Каждый PR сравнивать с одинаковым baseline и отклонять выигрыш скорости, который меняет bytecode semantics, порядок/seed, сохраняемые ресурсы или надёжность job lifecycle.
