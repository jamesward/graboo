import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.radioInput
import kotlinx.html.submitInput
import kotlinx.html.textInput
import kotlinx.html.title

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