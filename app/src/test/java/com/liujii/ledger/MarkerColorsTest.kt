package com.liujii.ledger

import com.liujii.ledger.ui.canonicalMarkerColor
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkerColorsTest {
    @Test
    fun oldCategoryColorsUseSevenRepresentativeColors() {
        val expected = mapOf(
            0xFFE8C264L to 0xFFE0A04BL,
            0xFF4CAF50L to 0xFF2FAE83L,
            0xFFE75F09L to 0xFFE0A04BL,
            0xFF667F74L to 0xFF778899L,
            0xFF9B63FFL to 0xFF9A78C6L,
            0xFFD67862L to 0xFFE76F64L,
            0xFF2FBE83L to 0xFF2FAE83L,
            0xFF778899L to 0xFF778899L,
            0xFF2DB7B4L to 0xFF2DB7B4L,
            0xFF808080L to 0xFF778899L,
            0xFF84847EL to 0xFF778899L
        )
        expected.forEach { (oldColor, representative) ->
            assertEquals(representative, canonicalMarkerColor(oldColor))
        }
    }
}
