package com.example.myapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class GreetingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun myTest() {
        composeTestRule.setContent {
            Greeting(name = "Test")
        }

        composeTestRule.onNodeWithText("Hello Test!").assertIsDisplayed()
    }

}