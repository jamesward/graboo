import kotlinx.browser.document

fun main() {

    println("hello, world")
    document.addEventListener("submit") {
        println("submit")
    }

}