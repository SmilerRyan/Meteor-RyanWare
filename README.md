<p align="center">
  <img src="knowledge_book.png" width="100" alt="Knowledge Book"/>
</p>

<h1 align="center">RyanWare Meteor Addon</h1>
<p align="center">A powerful addon for Meteor Client designed to make things easier</p>

<p align="center">
  <b>Automation • Combat • Chat • Entertainment • AI • Utility • Experimental</b>
</p>

<p align="center">
    [<a href="https://github.com/SmilerRyan/Meteor-RyanWare">View on GitHub</a>]
    [<a href="https://github.com/SmilerRyan/Meteor-RyanWare/archive/refs/heads/main.zip">Download source as zip</a>]
    [<a href="#installation">Installation Guide</a>]
    [<a href="https://github.com/SmilerRyan/Meteor-RyanWare/commits/main/">Commit History</a>]
</p>

---

## Overview

**RyanWare** is a large **addon** that adds dozens of modules, commands, and utilities for you to use on **Meteor Client**.

This is **NOT** a standalone client, this is built **on top of** Meteor Client, and it is required for this addon to be run.

This addon is aimed for the latest version of Minecraft + Meteor Client, not older versions.<br>
Using this addon on any version that is not the latest should load, but it isn't supported.

Many modules are experimental, educational or server dependent.<br>
You are responsible for any usage, including server bans, use it responsibly.<br>
Using it's features on servers that give you any advanage could be considered cheating.

---

<b id="installation"></b>
## Installation

### Option A: Install seperately as an Addon
1. Install Meteor Client, currently (<a href="meteor-client-1.21.11-82.jar">meteor-client-1.21.11-82.jar</a>) works best.
2. Build the RyanWare `.jar` from source or <a href="RyanWare-addon.jar">download it from GitHub</a> directly.
3. Put Meteor Client and RyanWare in your `.minecraft/mods` folder.
4. Launch the game with Fabric.

### Option B: Install Standalone (With Meteor Client 1.21.11 included)
1. Download the standalone version from GitHub: <a href="RyanWare-standalone.jar">RyanWare-standalone.jar</a>
2. Put `RyanWare-standalone.jar` in your `.minecraft/mods` folder.
3. Launch the game using Fabric 1.21.11 (required).

---

## Building

You need to download <a href="https://services.gradle.org/distributions/gradle-8.13-bin.zip">Gradle 8.13</a> and <a href="https://download.oracle.com/java/21/latest/jdk-21_windows-x64_bin.zip">Java 21 JDK</a> before the build step will work.
<br>
The build script will use the ``gradle-8.13`` and ``jdk-21.0.11`` directories in this automatically if found, or just manually run:

```
./gradlew clean build --no-daemon
````

Output:

```
./build/libs/meteor-RyanWare-0.1-addon.jar
./build/libs/meteor-RyanWare-0.1-standalone.jar
```

---

## License and credits

Licensed under **GPL v3** (The same as Meteor Client).

You must:

* Open-source your project if using this code
* Credit this project
* Use the same license

Credits:

* Meteor Development Team (For making Meteor Client and APIs) [<a href="https://github.com/MeteorDevelopment">Source</a>]
* AntiCope for their Meteor Crash Addon (used as addon starting base) [<a href="https://github.com/AntiCope/meteor-crash-addon/tree/0d64cc11330447d2821747f0b7f7566d6192b258">Source</a>]
* ChatGPT and other various AIs (For development on the RyanWare source code)
* All RyanWare contributors (For testing, suggesting, or providing code for this project)
