package com.classroomgrouping.platform;

import java.io.StringWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the pinned CSV dependency and its runtime links, not a business export contract. */
class DependencyCompatibilityTest {
    @Test void releasedCsvDependencyCanWriteAndReadQuotedUnicode() throws Exception {
        var output = new StringWriter();
        try (var printer = new CSVPrinter(output, CSVFormat.RFC4180)) {
            printer.printRecord("Unicode: 中文", "a,b\"c");
        }
        try (var parser = CSVParser.parse(output.toString(), CSVFormat.RFC4180)) {
            var records = parser.getRecords();
            assertEquals(1, records.size());
            assertEquals("Unicode: 中文", records.getFirst().get(0));
            assertEquals("a,b\"c", records.getFirst().get(1));
        }
    }
}
