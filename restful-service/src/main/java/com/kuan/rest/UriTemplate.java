package com.kuan.rest;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

interface UriTemplate {
    interface MatchResult extends Comparable<MatchResult> {
        String getMatched();

        String getRemaining();

        Map<String, String> getMatchedPathParameters();
    }

    Optional<MatchResult> match(String path);
}

class PathUriTemplate implements UriTemplate {

    private static final String LEFT_BRACKET = "\\{";
    private static final String RIGHT_BRACKET = "}";
    private static final String VARIABLE_NAME = "\\w[\\w.-]*";
    private static final String NON_BRACKETS = "[^\\{}]+";
    private static final Pattern variable = Pattern.compile(LEFT_BRACKET + group(VARIABLE_NAME) +
            group(":" + group(NON_BRACKETS)) + "?" + RIGHT_BRACKET);
    private static final int variableNameGroup = 1;
    private static final int variablePatternGroup = 3;

    private static final String defaultVariablePattern = "([^/]+?)";


    private final Pattern pattern;
    private final List<String> variables = new ArrayList<>();
    private final int variableGroupStartFrom;
    private int specificPatternCount = 0;


    private static String group(String pattern) {
        return "(" + pattern + ")";
    }

    public PathUriTemplate(String template) {
        pattern = Pattern.compile(group(variable(template)) + "(/.*)?");
        variableGroupStartFrom = 2;
    }

    private String variable(String template) {
        return variable.matcher(template).replaceAll(result -> {
            String variableName = result.group(variableNameGroup);
            String pattern = result.group(variablePatternGroup);
            if (variables.contains(variableName)) {
                throw new IllegalArgumentException("duplicate variable " + variableName);
            }
            variables.add(variableName);

            if (pattern != null) {
                specificPatternCount++;
                return group(pattern);
            }
            return defaultVariablePattern;
        });
    }

    @Override
    public Optional<MatchResult> match(String path) {
        Matcher matcher = pattern.matcher(path);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new PathMatchResult(matcher));
    }

    class PathMatchResult implements MatchResult {
        private final int specificPatternCount;
        private int matchLiteralCount;
        private final Matcher matcher;
        private final int count;
        private final Map<String, String> parameters = new HashMap<>();

        public PathMatchResult(Matcher matcher) {
            this.matcher = matcher;
            this.count = matcher.groupCount();
            this.matchLiteralCount = matcher.group(variableNameGroup).length();
            this.specificPatternCount = PathUriTemplate.this.specificPatternCount;

            for (int i = 0; i < variables.size(); i++) {
                parameters.put(variables.get(i), matcher.group(variableGroupStartFrom + i));
                matchLiteralCount -= matcher.group(variableGroupStartFrom + i).length();
            }
        }

        @Override
        public String getMatched() {
            return matcher.group(variableNameGroup);
        }

        @Override
        public String getRemaining() {
            return matcher.group(count);
        }

        @Override
        public Map<String, String> getMatchedPathParameters() {
            return parameters;
        }

        @Override
        public int compareTo(MatchResult o) {
            PathMatchResult result = (PathMatchResult) o;
            if (this.matchLiteralCount > result.matchLiteralCount) {
                return -1;
            }
            if (this.matchLiteralCount < result.matchLiteralCount) {
                return 1;
            }
            if (this.parameters.size() > result.parameters.size()) {
                return -1;
            }
            if (this.parameters.size() < result.parameters.size()) {
                return 1;
            }
            if (this.specificPatternCount > result.specificPatternCount) {
                return -1;
            }
            if (this.specificPatternCount < result.specificPatternCount) {
                return 1;
            }
            return 0;
        }
    }


}
