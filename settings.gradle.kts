
rootProject.name = "resourced"
include("modules:utils", "modules:cio", "modules:storaged")
findProject(":modules:utils")?.name = "utils"
findProject(":modules:cio")?.name = "cio"
findProject(":modules:storaged")?.name = "storaged"
