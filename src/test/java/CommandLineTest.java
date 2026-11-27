import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandLineTest {
    @Test
    void joinsQuotedFragmentsAndPreservesEmptyArguments() {
        var command = CommandLine.parse("echo\t'' a' b'\" c\" \"\" ' two  spaces '").commands().getFirst();
        assertEquals(List.of("echo", "", "a b c", "", " two  spaces "), command.arguments());
    }

    @Test
    void distinguishesQuotedAndEscapedOperatorsFromSyntax() {
        var line = CommandLine.parse("echo '|' \"2>\" \\& \\> \"&\"");
        assertFalse(line.background());
        assertEquals(List.of("echo", "|", "2>", "&", ">", "&"), line.commands().getFirst().arguments());
        assertTrue(line.commands().getFirst().redirects().isEmpty());
    }

    @Test
    void appliesBackslashesAccordingToQuoteContext() {
        var command = CommandLine.parse("echo a\\ b '\\q' \"\\q \\\" \\\\ \\$\" \\\\")
                .commands().getFirst();
        assertEquals(List.of("echo", "a b", "\\q", "\\q \" \\ $", "\\"), command.arguments());
    }

    @Test
    void parsesAdjacentOperatorsAndKeepsRedirectsOnTheirOwnStage() {
        var line = CommandLine.parse("  tool2>>'out file' 2>err|next 1>>output&  ");
        assertTrue(line.background());
        assertEquals("tool2>>'out file' 2>err|next 1>>output", line.display());
        assertEquals(List.of("tool2"), line.commands().getFirst().arguments());
        assertEquals(List.of(new CommandLine.Redirect("out file", false, true),
                new CommandLine.Redirect("err", true, false)), line.commands().getFirst().redirects());
        assertEquals(List.of(new CommandLine.Redirect("output", false, true)), line.commands().getLast().redirects());
    }

    @Test
    void treatsSeparatedAndQuotedDescriptorNumbersAsArguments() {
        var command = CommandLine.parse("echo 2 >file '2'>other").commands().getFirst();
        assertEquals(List.of("echo", "2", "2"), command.arguments());
        assertTrue(command.redirects().stream().noneMatch(CommandLine.Redirect::error));
    }

    @Test
    void returnsNoCommandsForBlankInput() {
        assertTrue(CommandLine.parse(" \t ").commands().isEmpty());
        assertTrue(CommandLine.parse("").commands().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"echo 'x", "echo \"x", "echo x\\", "| echo", "echo |", "echo || pwd",
            "echo >", "echo 2>>| cat", "echo & pwd", "&", "echo &&", ">file"})
    void rejectsMalformedCommands(String input) {
        assertThrows(CommandLine.SyntaxException.class, () -> CommandLine.parse(input));
    }
}
