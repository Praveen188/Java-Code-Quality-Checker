plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.javaqualitychecker"
version = "1.0.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.3")
    type.set("IC")
    plugins.set(listOf("com.intellij.java"))
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("261.*")
        changeNotes.set("""
            <ul>
                <li>1.0.0 — Initial release: spell check on code identifiers,
                context-aware naming suggestions, readability analysis,
                Javadoc detection and bug detection. FREE with Google Gemini.</li>
            </ul>
        """)
    }
}
