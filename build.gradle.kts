plugins {
    id("fabric-loom") version "1.9-SNAPSHOT"
}

base {
    archivesName = properties["archives_base_name"] as String
    version = properties["mod_version"] as String
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        name = "seedfinding-maven"
        url = uri("https://maven.seedfinding.com/")
    }
    maven {
        name = "seedfinding-maven-snapshots"
        url = uri("https://maven-snapshots.seedfinding.com/")
    }
}

dependencies {
    // Fabric
    minecraft("com.mojang:minecraft:${properties["minecraft_version"] as String}")
    mappings("net.fabricmc:yarn:${properties["yarn_mappings"] as String}:v2")
    modImplementation("net.fabricmc:fabric-loader:${properties["loader_version"] as String}")

    // Meteor
    modImplementation("meteordevelopment:meteor-client:${properties["minecraft_version"] as String}-SNAPSHOT")

    // Neil
    modImplementation("com.seedfinding:mc_math:ffd2edcfcc0d18147549c88cc7d8ec6cf21b5b91")
    modImplementation("com.seedfinding:mc_seed:1ead6fcefe7e8de4b3d60cd6c4e993f1e8f33409")
    modImplementation("com.seedfinding:mc_core:1.210.0")
    modImplementation("com.seedfinding:mc_noise:7e3ba65e181796c4a2a1c8881d840b2254b92962")
    modImplementation("com.seedfinding:mc_biome:41a42cb9019a552598f12089059538853e18ec78")
    modImplementation("com.seedfinding:mc_terrain:b4246cbd5880c4f8745ccb90e1b102bde3448126")
    modImplementation("com.seedfinding:mc_feature:919b7e513cc1e87e029a9cd703fc4e2dc8686229")
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to project.property("minecraft_version"),
        )

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        val licenseSuffix = project.base.archivesName.get()
        from("LICENSE") {
            rename { "${it}_${licenseSuffix}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
    }
}
