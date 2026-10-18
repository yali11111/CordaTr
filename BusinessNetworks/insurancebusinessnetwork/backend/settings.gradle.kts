rootProject.name = "insurance-api"

include(":contracts")
include(":workflows")

project(":contracts").projectDir = file("../contracts")
project(":workflows").projectDir = file("../workflows")