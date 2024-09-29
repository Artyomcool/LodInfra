grammar Structs;

@header {
package com.github.artyomcool.lodinfra.dateditor.grammar.gen;
}

root
    : (element ';')+
    ;

element
    : structSpecifier
    | enumSpecifier
    ;

typeSpecifier
    : generics
    | Identifier
    ;

genericsTypeName
    : Identifier
    ;

genericParam
    : initializer
    | typeSpecifier
    ;

generics
    : genericsTypeName '<' genericParam (',' genericParam)* '>';

structSpecifier
    : 'struct' Identifier '{' structDeclaration+ '}'
    ;

enumSpecifier
    : 'enum' Identifier '{' (enumerationValue ',')* enumerationValue? '}'
    ;

enumerationValue
    : Identifier
    ;

structDeclaration
    : 'REFLECT_MEMBER' '(' typeSpecifier ',' Identifier (',' initializer)? ')'
    | 'REFLECT_MEMBER_R' '(' '(' typeSpecifier ')' ',' Identifier (',' initializer)? ')'
    | element ';'
    ;

initializer
    : Constant
    | StringLiteral+
    ;

Constant
    : '-'? IntegerConstant
    | '-'? FloatingConstant
    | 'TRUE'
    | 'FALSE'
    //|   EnumerationConstant
    //| CharacterConstant
    ;

fragment IntegerConstant
    : DecimalConstant
    //| HexadecimalConstant
    ;

fragment DecimalConstant
    : NonzeroDigit Digit*
    | '0'
    ;
/*
fragment HexadecimalConstant
    : HexadecimalPrefix HexadecimalDigit+
    ;

fragment HexadecimalPrefix
    : '0' [xX]
    ;
*/

fragment HexadecimalDigit
    : [0-9a-fA-F]
    ;

fragment FloatingConstant
    : FractionalConstant ExponentPart?
    | DigitSequence ExponentPart
    ;

fragment FractionalConstant
    : DigitSequence? '.' DigitSequence
    | DigitSequence '.'
    ;

fragment ExponentPart
    : [eE] Sign? DigitSequence
    ;

fragment Sign
    : [+-]
    ;
/*
fragment CharacterConstant
    : CChar+
    ;

fragment CChar
    : ~['\\\r\n]
    | EscapeSequence
    ;*/

fragment EscapeSequence
    : SimpleEscapeSequence
    | HexadecimalEscapeSequence
    ;

fragment SimpleEscapeSequence
    : '\\' ['"?abfnrtv\\]
    ;

fragment HexadecimalEscapeSequence
    : '\\x' HexadecimalDigit+
    ;

StringLiteral
    : '"' SCharSequence? '"'
    ;

fragment SCharSequence
    : SChar+
    ;

fragment SChar
    : ~["\\\r\n]
    | EscapeSequence
    | '\\\n'   // Added line
    | '\\\r\n' // Added line
    ;

fragment Digit
    : [0-9]
    ;

fragment NonzeroDigit
    : [1-9]
    ;

fragment Nondigit
    : [a-zA-Z_]
    ;

DigitSequence
    : Digit+
    ;

Identifier
    : Nondigit (Nondigit | Digit)*
    ;

Whitespace
    : [ \t]+ -> channel(HIDDEN)
    ;

Newline
    : ('\r' '\n'? | '\n') -> channel(HIDDEN)
    ;

LineComment
    : '//' ~[\r\n]* -> channel(HIDDEN)
    ;