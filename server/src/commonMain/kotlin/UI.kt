import kotlinx.html.*

object UI {
    val index: HTML.() -> Unit = {
        head {
            title {
                +"Graboo"
            }
        }
        body {
            h1 {
                +"Graboo! Boot a new Gradle project:"
            }
            form(action = "/download", method = FormMethod.post) {
                label {
                    +"Project Name"

                    textInput(name = "project_name") {
                        value = "demo"
                        required = true
                    }
                }

                fieldSet {
                    legend {
                        +"Project Type"
                    }

                    Archetype.entries.forEach {
                        label {
                            radioInput(name = "project_type") {
                                value = it.toString()
                                required = true

                                +it.toString()
                            }
                        }
                    }
                }

                submitInput {
                    value = "Download Project Zip"
                }
            }
        }
    }
}