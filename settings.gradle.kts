
rootProject.name = "resourced"
include("modules:utils", "modules:storaged")
findProject(":modules:utils")?.name = "utils"
findProject(":modules:storaged")?.name = "storaged"
