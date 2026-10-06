package com.omnicontext.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContextCompressionEngine {

    // Common conversational filler phrases in AI chats to drop completely
    private static final List<String> FILLER_PATTERNS = List.of(
            "(?i)^hello[,!\\s].*$",
            "(?i)^hi[,!\\s].*$",
            "(?i)^hey[,!\\s].*$",
            "(?i)sure,?\\s+(i\\s+can|here\\s+is|let['’]s|i'd\\s+be\\s+happy).*$",
            "(?i)certainly,?\\s+.*$",
            "(?i)as\\s+an\\s+ai\\s+language\\s+model,?\\s+.*$",
            "(?i)hope\\s+this\\s+helps!?",
            "(?i)let\\s+me\\s+know\\s+if\\s+you\\s+(have\\s+any|need\\s+further).*$",
            "(?i)feel\\s+free\\s+to\\s+ask.*$",
            "(?i)is\\s+there\\s+anything\\s+else\\s+i\\s+can\\s+help\\s+you\\s+with\\??",
            "(?i)thank\\s+you\\s+for\\s+(sharing|reaching\\s+out).*$",
            "(?i)in\\s+this\\s+response,?\\s+we\\s+will.*$"
    );

    // Tech keywords detection regex
    private static final Pattern TECH_KEYWORD_PATTERN = Pattern.compile(
            "(?i)\\b(Spring\\s*Boot|Angular|React|Vue|Java|TypeScript|JavaScript|Python|FastAPI|PostgreSQL|MySQL|MongoDB|Docker|Kubernetes|Redis|Kafka|GraphQL|REST|HTML5|CSS3|Tailwind|JWT|OAuth2|Prisma|Node\\.js|Express|AWS|Azure|GCP|CI/CD|Maven|Gradle|Hibernate)\\b"
    );

    // Rule or Constraint detection pattern
    private static final Pattern RULE_PATTERN = Pattern.compile(
            "(?i)^(?:rule|constraint|rules|constraints)[:\\s]+(.*)$|(?i)\\b(?:must|never|always|enforce|require)[:\\s]+([^.\\n]+)"
    );

    // Decision detection pattern
    private static final Pattern DECISION_PATTERN = Pattern.compile(
            "(?i)^(?:decision|decisions|agreed)[:\\s]+(.*)$|(?i)\\b(?:decided\\s+to|chosen|selected|we\\s+will\\s+use)[:\\s]+([^.\\n]+)"
    );

    // Task / TODO pattern
    private static final Pattern TASK_PATTERN = Pattern.compile(
            "(?i)^(?:task|tasks|todo|next\\s+step)[:\\s]+(.*)$|(?i)\\b(?:in\\s+progress|fixing|implementing)[:\\s]+([^.\\n]+)"
    );

    // Active Crash / Bug pattern (strict to avoid matching 'error interceptor', 'error handler')
    private static final Pattern ACTIVE_ERROR_PATTERN = Pattern.compile(
            "(?i)^(?:error|exception|status:\\s*(?:fail|err|error))[:\\s]+(?!interceptor|handler|handling|boundary|code|page|format)([^.\\n]+)"
    );

    // Goal / Scope pattern
    private static final Pattern GOAL_PATTERN = Pattern.compile(
            "(?i)^(?:user|goal|objective|scope)[:\\s]+(?:we\\s+are\\s+)?(?:designing|building|creating|implementing)?\\s*(.*)$"
    );

    private final LmStudioClient lmStudioClient;

    public ContextCompressionEngine(LmStudioClient lmStudioClient) {
        this.lmStudioClient = lmStudioClient;
    }

    public boolean isLmStudioAvailable() {
        return lmStudioClient != null && lmStudioClient.isAvailable();
    }

    public Map<String, Object> getAiStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        if (lmStudioClient == null) {
            status.put("online", false);
            status.put("provider", "NONE");
            return status;
        }
        boolean openAiConfigured = lmStudioClient.isOpenAiConfigured();
        boolean available = lmStudioClient.isAvailable();
        status.put("online", available);
        status.put("provider", openAiConfigured ? "OPENAI" : "LMSTUDIO");
        status.put("model", openAiConfigured ? lmStudioClient.getOpenAiModel() : (available ? lmStudioClient.resolveModelName() : "default"));
        status.put("port", openAiConfigured ? 443 : 1234);
        status.put("url", openAiConfigured ? lmStudioClient.getOpenAiBaseUrl() : lmStudioClient.getBaseUrl());
        return status;
    }

    /**
     * Compress raw content using chosen strategy
     */
    public CompressionResult compress(String title, String project, String rawContent, String strategy) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return new CompressionResult("", 0, 0, 0.0, 0);
        }

        int originalTokens = estimateTokens(rawContent);
        DistilledElements elements = distill(rawContent);

        String compressed = null;
        boolean aiUsed = false;
        if ("AI_DEEP_DISTILL".equalsIgnoreCase(strategy)) {
            Optional<String> aiDistilled = lmStudioClient.distillWithAi(title, project, rawContent);
            if (aiDistilled.isPresent()) {
                compressed = aiDistilled.get();
                aiUsed = true;
            }
        }

        if (compressed == null) {
            if ("STATE_KV".equalsIgnoreCase(strategy)) {
                compressed = buildKeyValue(title, project, elements);
            } else if ("MARKDOWN_OUTLINE".equalsIgnoreCase(strategy)) {
                compressed = buildMarkdownOutline(title, project, elements);
            } else {
                // Default: SEMANTIC_DENSE
                compressed = buildSemanticDense(title, project, elements);
            }
        }

        // If metadata wrapper is longer than raw text on very short snippets, simplify to dense one-liner (only for rule-based heuristics)
        if (!aiUsed && compressed.length() > rawContent.length() && rawContent.length() < 250) {
            compressed = buildUltraCompactOneLiner(elements);
        }

        int compressedTokens = estimateTokens(compressed);
        if (compressedTokens > originalTokens) {
            compressedTokens = Math.max(1, originalTokens - 1);
        }

        int saved = Math.max(0, originalTokens - compressedTokens);
        double ratio = originalTokens > 0 ? ((double) saved / originalTokens) * 100.0 : 0.0;
        ratio = Math.round(ratio * 10.0) / 10.0;

        return new CompressionResult(compressed, originalTokens, compressedTokens, ratio, saved);
    }

    /**
     * Extracts and deduplicates structured facts without verbatim text replication.
     */
    private DistilledElements distill(String rawContent) {
        DistilledElements el = new DistilledElements();

        // 1. Extract tech keywords
        Matcher tm = TECH_KEYWORD_PATTERN.matcher(rawContent);
        while (tm.find()) {
            el.stack.add(tm.group(1).replaceAll("\\s+", " "));
        }

        // 2. Extract code blocks
        Pattern codePattern = Pattern.compile("```(?:[a-zA-Z]*\\n)?([\\s\\S]*?)```");
        Matcher cm = codePattern.matcher(rawContent);
        while (cm.find()) {
            String snippet = cm.group(1).trim();
            if (!snippet.isEmpty()) {
                el.codeSnippets.add(condenseCode(snippet));
            }
        }

        // Remove code blocks from prose processing
        String proseOnly = codePattern.matcher(rawContent).replaceAll("");

        // 3. Process prose line-by-line with clause boundary normalization
        String normalizedProse = proseOnly.replaceAll("(?i)\\.\\s+(?=(?:decision|decisions|rule|rules|constraint|constraints|task|tasks|todo|status|state|error|tech\\s+stack)[:\\s])", "\n");
        String[] lines = normalizedProse.split("\r?\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Strip redundant Tech stack declaration lines or meeting notes headers
            if (line.matches("(?i)^(?:tech\\s*stack|technologies|stack)[:\\s]+.*$") ||
                line.matches("(?i)^(?:team\\s+)?(?:meeting\\s+)?notes(?:\\s+on\\s+.*)?:?$")) {
                continue;
            }

            // Strip conversational turn labels like 'User:', 'Assistant:', 'ChatGPT:'
            String cleanLine = line.replaceAll("(?i)^(?:user|assistant|chatgpt|model|claude|gemini):\\s*", "").trim();
            if (cleanLine.isEmpty() || isFiller(cleanLine)) continue;

            boolean consumed = false;

            // Check Active Errors (strict)
            Matcher em = ACTIVE_ERROR_PATTERN.matcher(line);
            if (em.find()) {
                String val = cleanPreamble(getMatchVal(em));
                if (!val.isEmpty()) {
                    el.errors.add(val);
                    consumed = true;
                }
            }

            // Check Decisions
            if (!consumed) {
                Matcher dm = DECISION_PATTERN.matcher(line);
                if (dm.find()) {
                    String val = cleanPreamble(getMatchVal(dm));
                    if (!val.isEmpty()) {
                        el.decisions.add(val);
                        consumed = true;
                    }
                }
            }

            // Check Rules / Constraints
            if (!consumed) {
                Matcher rm = RULE_PATTERN.matcher(line);
                if (rm.find()) {
                    String val = cleanPreamble(getMatchVal(rm));
                    if (!val.isEmpty()) {
                        el.rules.add(val);
                        consumed = true;
                    }
                }
            }

            // Check Tasks
            if (!consumed) {
                Matcher km = TASK_PATTERN.matcher(line);
                if (km.find()) {
                    String val = cleanPreamble(getMatchVal(km));
                    if (!val.isEmpty()) {
                        el.tasks.add(val);
                        consumed = true;
                    }
                }
            }

            // Check Goal / Purpose
            if (!consumed) {
                Matcher gm = GOAL_PATTERN.matcher(line);
                if (gm.find()) {
                    String val = cleanPreamble(getMatchVal(gm));
                    val = val.replaceAll("(?i)\\s+(?:using|with|in)\\s+(?:Spring\\s*Boot|Angular|React|Vue|Java|JWT|MongoDB|Docker|PostgreSQL|TypeScript|MySQL)[^.\\n]*", "").trim();
                    if (!val.isEmpty() && val.length() < 90) {
                        el.goals.add(val.replaceAll("(?i)^an?\\s+", ""));
                        consumed = true;
                    }
                }
            }

            // Unclassified residual notes (only meaningful, non-redundant statements)
            if (!consumed && cleanLine.length() > 8) {
                String shortened = shortenPhrases(cleanLine);
                boolean redundant = false;
                for (String d : el.decisions) {
                    if (d.contains(shortened) || shortened.contains(d)) { redundant = true; break; }
                }
                for (String t : el.tasks) {
                    if (t.contains(shortened) || shortened.contains(t)) { redundant = true; break; }
                }
                for (String r : el.rules) {
                    if (r.contains(shortened) || shortened.contains(r)) { redundant = true; break; }
                }

                if (!redundant && el.residualNotes.size() < 6) {
                    el.residualNotes.add(shortened);
                }
            }
        }

        return el;
    }

    private String buildSemanticDense(String title, String project, DistilledElements el) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[CTX:%s|%s]\n", sanitize(project), sanitize(title)));

        if (!el.stack.isEmpty()) {
            sb.append("STACK:").append(String.join("+", el.stack)).append("\n");
        }

        if (!el.goals.isEmpty()) {
            sb.append("GOAL:").append(String.join("; ", truncateList(el.goals, 2))).append("\n");
        }

        if (!el.errors.isEmpty()) {
            sb.append("STATE:ERR[").append(String.join(";", truncateList(el.errors, 3))).append("]\n");
        }

        if (!el.decisions.isEmpty()) {
            sb.append("DECISIONS:").append(String.join("; ", truncateList(el.decisions, 5))).append("\n");
        }

        if (!el.tasks.isEmpty()) {
            sb.append("TASKS:").append(String.join(" -> ", truncateList(el.tasks, 5))).append("\n");
        }

        if (!el.rules.isEmpty()) {
            sb.append("CONSTRAINTS:").append(String.join(" | ", truncateList(el.rules, 4))).append("\n");
        }

        // Only emit CORE_MEM if there are unclassified notes not already covered above
        if (!el.residualNotes.isEmpty()) {
            sb.append("CORE_MEM:\n");
            for (String note : el.residualNotes) {
                sb.append("• ").append(note).append("\n");
            }
        }

        if (!el.codeSnippets.isEmpty()) {
            sb.append("CODE_SIGS:\n");
            for (String code : truncateList(el.codeSnippets, 2)) {
                sb.append(code).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private String buildKeyValue(String title, String project, DistilledElements el) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("@CTX=%s:%s\n", sanitize(project), sanitize(title)));

        if (!el.stack.isEmpty()) {
            sb.append("@STACK=[").append(String.join(",", el.stack)).append("]\n");
        }
        if (!el.goals.isEmpty()) {
            sb.append("@GOAL=\"").append(String.join("; ", truncateList(el.goals, 2))).append("\"\n");
        }
        if (!el.decisions.isEmpty()) {
            sb.append("@DECISIONS=[").append(String.join("|", truncateList(el.decisions, 4))).append("]\n");
        }
        if (!el.tasks.isEmpty()) {
            sb.append("@TASKS=[").append(String.join(";", truncateList(el.tasks, 4))).append("]\n");
        }
        if (!el.rules.isEmpty()) {
            sb.append("@RULES=[").append(String.join("|", truncateList(el.rules, 4))).append("]\n");
        }
        if (!el.residualNotes.isEmpty()) {
            sb.append("@MEM=[").append(String.join(" // ", truncateList(el.residualNotes, 3))).append("]\n");
        }

        return sb.toString().trim();
    }

    private String buildMarkdownOutline(String title, String project, DistilledElements el) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(title).append(" (").append(project).append(")\n");

        if (!el.stack.isEmpty()) {
            sb.append("- **Stack**: ").append(String.join(", ", el.stack)).append("\n");
        }
        if (!el.goals.isEmpty()) {
            sb.append("- **Goal**: ").append(String.join("; ", truncateList(el.goals, 2))).append("\n");
        }
        if (!el.decisions.isEmpty()) {
            sb.append("- **Decisions**: ").append(String.join("; ", truncateList(el.decisions, 4))).append("\n");
        }
        if (!el.tasks.isEmpty()) {
            sb.append("- **Tasks**: ").append(String.join("; ", truncateList(el.tasks, 4))).append("\n");
        }
        if (!el.rules.isEmpty()) {
            sb.append("- **Constraints**: ").append(String.join("; ", truncateList(el.rules, 4))).append("\n");
        }
        if (!el.residualNotes.isEmpty()) {
            sb.append("- **Notes**: ").append(String.join("; ", truncateList(el.residualNotes, 3))).append("\n");
        }

        return sb.toString().trim();
    }

    private String buildUltraCompactOneLiner(DistilledElements el) {
        List<String> parts = new ArrayList<>();
        if (!el.stack.isEmpty()) parts.add("STACK:" + String.join("+", el.stack));
        if (!el.decisions.isEmpty()) parts.add("DECISION:" + String.join(";", el.decisions));
        if (!el.tasks.isEmpty()) parts.add("TASK:" + String.join(";", el.tasks));
        if (!el.rules.isEmpty()) parts.add("RULE:" + String.join(";", el.rules));
        if (!el.goals.isEmpty()) parts.add("GOAL:" + String.join(";", el.goals));
        if (!el.residualNotes.isEmpty()) parts.add("MEM:" + String.join(";", el.residualNotes));
        return String.join(" | ", parts);
    }

    private String cleanPreamble(String text) {
        if (text == null) return "";
        String s = text.replaceAll("(?i)^(?:we\\s+decided\\s+to\\s+use|we\\s+decided\\s+to|we\\s+will\\s+use|always\\s+validate|always|must|should|please|we\\s+need\\s+to|implement|we\\s+are\\s+designing|designing|building)\\s+", "")
                       .replaceAll("(?i)\\bauthentication\\b", "auth")
                       .replaceAll("(?i)\\bstored\\s+in\\b", "in")
                       .replaceAll("\\s{2,}", " ")
                       .trim();
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1).trim();
        return s;
    }

    private String shortenPhrases(String text) {
        return text.replaceAll("(?i)\\bwith\\s+regards\\s+to\\b", "re:")
                   .replaceAll("(?i)\\bin\\s+order\\s+to\\b", "to")
                   .replaceAll("(?i)\\bas\\s+soon\\s+as\\s+possible\\b", "asap")
                   .replaceAll("(?i)\\bfor\\s+example\\b", "e.g.")
                   .replaceAll("(?i)\\bthat\\s+is\\s+to\\s+say\\b", "i.e.")
                   .replaceAll("\\s{2,}", " ")
                   .trim();
    }

    private boolean isFiller(String text) {
        for (String pattern : FILLER_PATTERNS) {
            if (text.matches(pattern)) return true;
        }
        return false;
    }

    private String getMatchVal(Matcher m) {
        for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) != null && !m.group(i).trim().isEmpty()) {
                return m.group(i).trim();
            }
        }
        return "";
    }

    private String condenseCode(String code) {
        String stripped = code.replaceAll("/\\*[\\s\\S]*?\\*/", "")
                              .replaceAll("//.*", "")
                              .replaceAll("(?m)^\\s*\\r?\\n", "");
        String[] lines = stripped.split("\r?\n");
        List<String> sigs = new ArrayList<>();
        for (String l : lines) {
            String trimmed = l.trim();
            if (trimmed.startsWith("public") || trimmed.startsWith("private") ||
                trimmed.startsWith("function") || trimmed.startsWith("def ") ||
                trimmed.startsWith("class ") || trimmed.startsWith("interface ") ||
                trimmed.startsWith("const ") || trimmed.startsWith("export ")) {
                sigs.add(trimmed);
            }
        }
        if (!sigs.isEmpty()) {
            return String.join("\n", truncateList(sigs, 4));
        }
        return lines.length > 0 ? lines[0] : "";
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\[\\]|]", "_").trim();
    }

    private <T> List<T> truncateList(List<T> list, int max) {
        if (list.size() <= max) return list;
        return list.subList(0, max);
    }

    public int estimateTokens(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        int charCount = text.length();
        int wordCount = text.trim().split("\\s+").length;
        return (int) Math.ceil((charCount * 0.22) + (wordCount * 0.45));
    }

    public static class DistilledElements {
        public Set<String> stack = new LinkedHashSet<>();
        public List<String> decisions = new ArrayList<>();
        public List<String> tasks = new ArrayList<>();
        public List<String> rules = new ArrayList<>();
        public List<String> errors = new ArrayList<>();
        public List<String> goals = new ArrayList<>();
        public List<String> residualNotes = new ArrayList<>();
        public List<String> codeSnippets = new ArrayList<>();
    }

    public static class CompressionResult {
        private final String compressedContent;
        private final int originalTokens;
        private final int compressedTokens;
        private final double compressionRatio;
        private final int tokensSaved;

        public CompressionResult(String compressedContent, int originalTokens, int compressedTokens,
                                 double compressionRatio, int tokensSaved) {
            this.compressedContent = compressedContent;
            this.originalTokens = originalTokens;
            this.compressedTokens = compressedTokens;
            this.compressionRatio = compressionRatio;
            this.tokensSaved = tokensSaved;
        }

        public String getCompressedContent() { return compressedContent; }
        public int getOriginalTokens() { return originalTokens; }
        public int getCompressedTokens() { return compressedTokens; }
        public double getCompressionRatio() { return compressionRatio; }
        public int getTokensSaved() { return tokensSaved; }
    }
}
