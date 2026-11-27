import java.util.ArrayList;
import java.util.List;

record CommandLine(List<Command> commands, boolean background, String display) {
    record Command(List<String> arguments, List<Redirect> redirects) {
        boolean redirectsOutput() {
            return redirects.stream().anyMatch(redirect -> !redirect.error());
        }
    }

    record Redirect(String file, boolean error, boolean append) {}

    private enum Kind { WORD, PIPE, BACKGROUND, REDIRECT }

    private record Token(Kind kind, String text, int start) {}

    static CommandLine parse(String line) {
        List<Token> tokens = tokenize(line);
        List<Command> commands = new ArrayList<>();
        if (tokens.isEmpty()) {
            return new CommandLine(List.of(), false, "");
        }

        List<String> arguments = new ArrayList<>();
        List<Redirect> redirects = new ArrayList<>();
        boolean background = false;
        String display = line.strip();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            switch (token.kind()) {
                case WORD -> arguments.add(token.text());
                case PIPE -> {
                    commands.add(command(arguments, redirects));
                    arguments.clear();
                    redirects.clear();
                }
                case BACKGROUND -> {
                    if (i != tokens.size() - 1) {
                        throw new SyntaxException("'&' must end the command");
                    }
                    background = true;
                    display = line.substring(0, token.start()).strip();
                }
                case REDIRECT -> {
                    if (++i == tokens.size() || tokens.get(i).kind() != Kind.WORD) {
                        throw new SyntaxException("expected a file after " + token.text());
                    }
                    redirects.add(new Redirect(tokens.get(i).text(),
                            token.text().startsWith("2"), token.text().endsWith(">>")));
                }
            }
        }
        commands.add(command(arguments, redirects));
        return new CommandLine(List.copyOf(commands), background, display);
    }

    private static Command command(List<String> arguments, List<Redirect> redirects) {
        if (arguments.isEmpty()) {
            throw new SyntaxException("expected a command");
        }
        return new Command(List.copyOf(arguments), List.copyOf(redirects));
    }

    private static List<Token> tokenize(String line) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            int start = i;
            char ch = line.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
            } else if (ch == '|' || ch == '&') {
                tokens.add(new Token(ch == '|' ? Kind.PIPE : Kind.BACKGROUND,
                        line.substring(i, ++i), start));
            } else if (ch == '>' || ((ch == '1' || ch == '2')
                    && i + 1 < line.length() && line.charAt(i + 1) == '>')) {
                i += ch == '>' ? 1 : 2;
                if (i < line.length() && line.charAt(i) == '>') {
                    i++;
                }
                tokens.add(new Token(Kind.REDIRECT, line.substring(start, i), start));
            } else {
                StringBuilder word = new StringBuilder();
                char quote = 0;
                while (i < line.length()) {
                    ch = line.charAt(i++);
                    if (quote == 0 && (Character.isWhitespace(ch) || ch == '|'
                            || ch == '&' || ch == '>')) {
                        i--;
                        break;
                    }
                    if (quote != 0 && ch == quote) {
                        quote = 0;
                    } else if (quote == 0 && (ch == '\'' || ch == '"')) {
                        quote = ch;
                    } else if (ch == '\\' && quote != '\'') {
                        if (i == line.length()) {
                            throw new SyntaxException("trailing backslash");
                        }
                        char next = line.charAt(i);
                        if (quote == 0 || next == '"' || next == '\\' || next == '$' || next == '`') {
                            word.append(next);
                            i++;
                        } else {
                            word.append(ch);
                        }
                    } else {
                        word.append(ch);
                    }
                }
                if (quote != 0) {
                    throw new SyntaxException("unterminated quote");
                }
                tokens.add(new Token(Kind.WORD, word.toString(), start));
            }
        }
        return tokens;
    }

    static final class SyntaxException extends IllegalArgumentException {
        SyntaxException(String message) {
            super(message);
        }
    }
}
