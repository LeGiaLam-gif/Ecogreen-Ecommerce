package com.example.backend.dto;

import com.example.backend.exception.BadRequestException;
import com.example.backend.service.ReturnItemDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** B07: the strict reader of the multipart {@code items} field. */
class ReturnItemsJsonTest {

    @Test
    void parsesTheDocumentedShape() {
        assertEquals(List.of(new ReturnItemDraft(101L, 2)),
                ReturnItemsJson.parse("[{\"orderItemId\":101,\"quantity\":2}]"));
    }

    @Test
    void parsesSeveralItems_whitespaceAndFieldOrder() {
        assertEquals(List.of(new ReturnItemDraft(1L, 2), new ReturnItemDraft(30L, 4)),
                ReturnItemsJson.parse("  [ { \"orderItemId\" : 1 , \"quantity\" : 2 } ,\n { \"quantity\": 4, \"orderItemId\": 30 } ]  "));
    }

    @Test
    void rejectsMissingBlankAndEmpty() {
        for (String bad : new String[] {null, "", "   ", "[]", "[ ]"}) {
            assertThrows(BadRequestException.class, () -> ReturnItemsJson.parse(bad), String.valueOf(bad));
        }
    }

    @Test
    void rejectsAnythingThatIsNotExactlyThatShape() {
        String[] bad = {
                "{\"orderItemId\":1,\"quantity\":2}",                       // not an array
                "[{\"orderItemId\":1}]",                                    // quantity missing
                "[{\"quantity\":2}]",                                       // orderItemId missing
                "[{\"orderItemId\":\"1\",\"quantity\":2}]",                 // a string, not a number
                "[{\"orderItemId\":1.5,\"quantity\":2}]",                   // not a whole number
                "[{\"orderItemId\":-1,\"quantity\":2}]",                    // negative
                "[{\"orderItemId\":1,\"quantity\":2,\"price\":1}]",         // an extra field (the price is never taken from the client)
                "[{\"orderItemId\":1,\"quantity\":2},]",                    // trailing comma
                "[,{\"orderItemId\":1,\"quantity\":2}]",                    // leading comma
                "[{\"orderItemId\":1,\"quantity\":2} {\"orderItemId\":2,\"quantity\":1}]", // no comma between objects
                "[{\"orderItemId\":1,\"quantity\":2}] x",                   // trailing text
                "[{\"orderItemId\":1,\"quantity\":2},garbage]",             // junk between objects
                "[{\"orderItemId\":99999999999999999999,\"quantity\":2}]",  // too long for a long
                "[{\"orderItemId\":1,\"quantity\":99999999999}]",           // too long for an int
                "[[{\"orderItemId\":1,\"quantity\":2}]]",                   // nested
                "[{\"orderItemId\":1,\"quantity\":2",                       // not closed
                "not json",
        };
        for (String input : bad) {
            assertThrows(BadRequestException.class, () -> ReturnItemsJson.parse(input), input);
        }
    }

    @Test
    void rejectsMoreThanAHundredItems() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 1; i <= 101; i++) {
            json.append(i > 1 ? "," : "").append("{\"orderItemId\":").append(i).append(",\"quantity\":1}");
        }
        json.append("]");

        assertThrows(BadRequestException.class, () -> ReturnItemsJson.parse(json.toString()));
    }

    @Test
    void zeroQuantity_isParsed_andRefusedLaterByTheService() {
        // The reader only checks the shape; the 1..ordered range is a business rule of ReturnService.
        assertEquals(List.of(new ReturnItemDraft(1L, 0)), ReturnItemsJson.parse("[{\"orderItemId\":1,\"quantity\":0}]"));
    }
}
