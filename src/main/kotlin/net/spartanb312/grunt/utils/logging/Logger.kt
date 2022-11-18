package net.spartanb312.grunt.utils.logging

object Logger : ILogger by SimpleLogger(
    "Grunt",
    //"logs/${SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())}.txt"
)