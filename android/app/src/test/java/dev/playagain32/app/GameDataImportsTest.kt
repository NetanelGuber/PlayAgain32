package dev.playagain32.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDataImportsTest {
    @Test fun onlyExpansionFilesForThisGameAreAccepted() {
        assertTrue(GameDataImports.validName("main.12.com.example.game.obb", "com.example.game"))
        assertTrue(GameDataImports.validName("patch.12.com.example.game.obb", "com.example.game"))
        assertFalse(GameDataImports.validName("../main.12.com.example.game.obb", "com.example.game"))
        assertFalse(GameDataImports.validName("main.12.com.other.game.obb", "com.example.game"))
        assertFalse(GameDataImports.validName("main.12.comXexampleXgame.obb", "com.example.game"))
    }
}
