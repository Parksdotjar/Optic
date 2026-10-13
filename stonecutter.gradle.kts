plugins { id("dev.kikugie.stonecutter") }
stonecutter active "1.21.11" /* [SC] DO NOT EDIT */

tasks.register("buildAll") {
    group = "build"
    description = "Build and test every supported Minecraft target."
    dependsOn(":1.21.11:build", ":26.2:build", ":26.3:build")
}
tasks.register("collectJars") {
    group = "build"
    description = "Collect verified version-specific mod jars in dist."
    dependsOn(":1.21.11:collectJar", ":26.2:collectJar", ":26.3:collectJar")
}
