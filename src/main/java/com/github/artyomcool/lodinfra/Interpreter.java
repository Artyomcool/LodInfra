package com.github.artyomcool.lodinfra;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class Interpreter {

    public static Object eval(String str, Map<String,?> args, Map<String, Function<List<Object>, Object>> functions) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            int next() {
                while (ch == ' ') nextChar();
                return ch;
            }

            Object parse() {
                nextChar();
                Object x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
                return x;
            }

            Object add(Object left, Object right) {
                left = unstring(left);
                right = unstring(right);
                if (left instanceof Double || right instanceof Double) {
                    Number ln = (Number) left;
                    Number rn = (Number) right;
                    return ln.doubleValue() + rn.doubleValue();
                }
                return (Long)left + (Long) right;
            }

            Object unaryMinus(Object obj) {
                if (obj instanceof Double) {
                    return -(Double)obj;
                }
                return -(Long)obj;
            }

            Object unstring(Object obj) {
                if (obj instanceof String) {
                    try {
                        return Long.parseLong((String) obj);
                    } catch (NumberFormatException e) {
                        return Double.parseDouble((String) obj);
                    }
                }
                return obj;
            }

            Object multiply(Object left, Object right) {
                left = unstring(left);
                right = unstring(right);
                if (left instanceof Double || right instanceof Double) {
                    Number ln = (Number) left;
                    Number rn = (Number) right;
                    return ln.doubleValue() * rn.doubleValue();
                }
                return (Long)left * (Long) right;
            }

            Object divide(Object left, Object right) {
                left = unstring(left);
                right = unstring(right);
                if (left instanceof Double || right instanceof Double) {
                    Number ln = (Number) left;
                    Number rn = (Number) right;
                    return ln.doubleValue() / rn.doubleValue();
                }
                return (Long)left / (Long) right;
            }

            Object callFunction(String name, List<Object> args) {
                return functions.get(name).apply(args);
            }

            Object parseExpression() {
                Object x = parseTerm();
                for (; ; ) {
                    switch (next()) {
                        case '+':
                            eat('+');
                            x = add(x, parseTerm());
                            break;
                        case '-':
                            eat('-');
                            x = add(x, unaryMinus(parseTerm()));
                            break;
                        case ';':
                            eat(';');
                            x = parseTerm();
                            break;
                        default:
                            return x;
                    }
                }
            }

            Object parseTerm() {
                Object x = parseFactor();
                for (; ; ) {
                    switch (next()) {
                        case '*':
                            eat('*');
                            x = multiply(x, parseFactor());
                            break;
                        case '/':
                            eat('/');
                            x = divide(x, parseFactor());
                            break;
                        default:
                            return x;
                    }
                }
            }

            Object parseFactor() {
                if (eat('+')) return parseFactor(); // unary plus
                if (eat('-')) return unaryMinus(parseFactor()); // unary minus

                int startPos = this.pos;
                switch (next()) {
                    case '(': {
                        eat('(');
                        Object x = parseExpression();
                        eat(')');
                        return x;
                    }
                    case '\'': {
                        eat('\'');
                        startPos = this.pos;
                        while (ch != '\'') {
                            nextChar();
                        }
                        String result = str.substring(startPos, this.pos);
                        eat('\'');
                        return result;
                    }
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                    case '.': {
                        boolean hasDot = ch == '.';
                        while ((ch >= '0' && ch <= '9') || ch == '.') {
                            nextChar();
                            hasDot |= ch == '.';
                        }
                        String x = str.substring(startPos, this.pos);
                        if (hasDot) {
                            return Double.valueOf(x);
                        }
                        return Long.valueOf(x);
                    }
                    default:
                        if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch == '_')) { // functions
                            while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || (ch == '_')) {
                                nextChar();
                            }
                            String name = str.substring(startPos, this.pos);
                            if (eat('(')){
                                if (eat(')')) {
                                    return callFunction(name, Collections.emptyList());
                                }

                                List<Object> args = parseArgs();
                                eat(')');
                                return callFunction(name, args);
                            } else if (eat('[')) {
                                Object expression = parseExpression();
                                if (expression instanceof String) {
                                    expression = Integer.parseInt((String) expression);
                                }
                                int e = ((Number) expression).intValue();
                                eat(']');
                                List<?> lst = (List<?>) getArg(name);
                                return lst.get(e);
                            }
                            return getArg(name);
                        } else {
                            throw new RuntimeException("Unexpected: " + (char) ch);
                        }
                }
            }

            private Object getArg(String name) {
                Object result = args.get(name);
                if (result instanceof Supplier) {
                    return ((Supplier<?>) result).get();
                }
                return result;
            }

            List<Object> parseArgs() {
                List<Object> args = new ArrayList<>();
                args.add(parseExpression());
                while (eat(',')) {
                    args.add(parseExpression());
                }
                return args;
            }

        }.parse();
    }
}
