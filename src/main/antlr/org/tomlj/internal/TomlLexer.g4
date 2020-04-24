lexer grammar TomlLexer;

channels { COMMENTS, WHITESPACE }

tokens { TripleQuotationMark, TripleApostrophe, StringChar, Comma }

@header {
package org.tomlj.internal;
}

@members {
  private final IntegerStack arrayDepthStack = new IntegerStack();
  private int arrayDepth = 0;

  private void resetArrayDepth() {
    arrayDepthStack.clear();
    arrayDepth = 0;
  }

  private void pushArrayDepth() {
    arrayDepthStack.push(arrayDepth);
    arrayDepth = 0;
  }

  private void popArrayDepth() {
    arrayDepth = arrayDepthStack.pop();
  }
}

fragment WSChar : [ \t];
fragment NL : '\r'? '\n';
fragment COMMENT : '#' (~'\n')*;
fragment Alpha : [A-Za-z];
fragment Digit : [0-9];
fragment Digit1_9 : [1-9];
fragment Digit0_7 : [0-7];
fragment Digit0_1 : [0-1];
fragment HexDig : Digit | [A-Fa-f];

fragment KeyChar : (Alpha | Digit | '-' | '_');
fragment UNQUOTED_KEY : KeyChar+;
fragment LENIENT_UNQUOTED_KEY : KeyChar | KeyChar (KeyChar | WSChar)* KeyChar;

Dot : '.';
Equals : '=' { resetArrayDepth(); } -> pushMode(ValueMode);
QuotationMark : '"' -> pushMode(BasicStringMode);
Apostrophe : '\'' -> pushMode(LiteralStringMode);
TableKeyStart : '[';
TableKeyEnd : ']';
ArrayTableKeyStart : '[[';
ArrayTableKeyEnd : ']]';
UnquotedKey : UNQUOTED_KEY;

WS : WSChar+ -> channel(WHITESPACE);
Comment : COMMENT -> channel(COMMENTS);
NewLine : NL { setText(System.lineSeparator()); };
Error : .;


mode KeyMode;

KeyDot : '.' -> type(Dot);
KeyQuotationMark : '"' -> type(QuotationMark), pushMode(BasicStringMode);
KeyApostrophe : '\'' -> type(Apostrophe), pushMode(LiteralStringMode);
KeyUnquotedKey : UNQUOTED_KEY -> type(UnquotedKey);

KeyWS : WSChar+ -> type(WS), channel(WHITESPACE);
KeyError : . -> type(Error);

mode TomlKeyMode;

TomlKeyDot : '.' -> type(Dot);
TomlKeyQuotationMark : '"' -> type(QuotationMark), pushMode(BasicStringMode);
TomlKeyApostrophe : '\'' -> type(Apostrophe), pushMode(LiteralStringMode);
TomlKeyUnquotedKey : LENIENT_UNQUOTED_KEY -> type(UnquotedKey);

TomlKeyWS : WSChar+ -> type(WS), channel(WHITESPACE);
TomlKeyError : . -> type(Error);

mode ValueMode;

// Strings
ValueQuotationMark : '"' -> type(QuotationMark), mode(BasicStringMode);
ValueTripleQuotationMark : '"""' NL? -> type(TripleQuotationMark), mode(MLBasicStringMode);
ValueApostrophe : '\'' -> type(Apostrophe), mode(LiteralStringMode);
ValueTripleApostrophe : '\'\'\'' NL? -> type(TripleApostrophe), mode(MLLiteralStringMode);

// Integers
fragment DecInt : [-+]? (Digit | Digit1_9 ('_'? Digit)+);
DecimalInteger : DecInt { "-:".indexOf(_input.LA(1)) < 0 }? -> popMode;
HexInteger : '0x' HexDig ('_'? HexDig)* -> popMode;
OctalInteger : '0o' Digit0_7 ('_'? Digit0_7)* -> popMode;
BinaryInteger : '0b' Digit0_1 ('_'? Digit0_1)* -> popMode;

// Float
fragment Exp : [eE] [-+]? [0]? (Digit | Digit1_9 ('_'? Digit)+);
fragment Frac : '.' Digit ('_'? Digit)*;
FloatingPoint : DecInt (Exp | Frac Exp?) -> popMode;
FloatingPointInf: [-+]? 'inf' -> popMode;
FloatingPointNaN : [-+]? 'nan' -> popMode;

// Boolean
TrueBoolean : 'true' -> popMode;
FalseBoolean : 'false' -> popMode;

// Date and Time
ValueDateStart : Digit+ { "-:".indexOf(_input.LA(1)) >= 0 }? -> type(DateDigits), mode(DateMode);

// Array
ArrayStart : '[' { arrayDepth++; } -> pushMode(ValueMode);
ArrayComma : ',' { arrayDepth > 0 }? -> type(Comma), pushMode(ValueMode);
ArrayEnd : ']' { arrayDepth--; } -> popMode;

// Table
InlineTableStart : '{' { pushArrayDepth(); } -> mode(InlineTableMode);

ValueWS : WSChar+ -> type(WS), channel(WHITESPACE);
ValueComment : COMMENT -> type(Comment), channel(COMMENTS);
ArrayNewLine: NL { arrayDepth > 0}? -> type(NewLine);

ValueNewLine: NL { arrayDepth == 0}? -> type(NewLine), popMode;
ValueError : . -> type(Error), popMode;


mode BasicStringMode;

BasicStringEnd : '"' -> type(QuotationMark), popMode;
BasicStringUnescaped : ~[\u0000-\u0008\u000A-\u001F"\\\u007F] -> type(StringChar);
EscapeSequence
  : '\\' ~[\n]
  | '\\u' HexDig HexDig HexDig HexDig
  | '\\U' HexDig HexDig HexDig HexDig HexDig HexDig HexDig HexDig;

BasicStringNewLine: NL { setText(System.lineSeparator()); } -> type(NewLine), popMode;
BasicStringError : . -> type(Error), popMode;


mode MLBasicStringMode;

MLBasicStringEnd : '""' -> type(TripleQuotationMark), popMode;
MLBasicStringLineEnd : '\\' [ \t]* NL { setText(System.lineSeparator()); } -> type(NewLine);
MLBasicStringUnescaped : ~[\u0000-\u0008\u000C\u000E-\u001F\\\u007F] -> type(StringChar);
MLBasicStringEscape :
  ('\\u' HexDig HexDig HexDig HexDig
  | '\\U' HexDig HexDig HexDig HexDig HexDig HexDig HexDig HexDig
  | '\\' .) -> type(EscapeSequence);
MLBasicStringNewLine: NL { setText(System.lineSeparator()); } -> type(NewLine);

MLBasicStringError : . -> type(Error), popMode;


mode LiteralStringMode;

LiteralStringEnd : '\'' -> type(Apostrophe), popMode;
LiteralStringChar : ~[\u0000-\u0008\u000A-\u001F'\u007F] -> type(StringChar);

LiteralStringNewLine: NL { setText(System.lineSeparator()); } -> type(NewLine), popMode;
LiteralStringError : . -> type(Error), popMode;


mode MLLiteralStringMode;

MLLiteralStringEnd : '\'\'\'' -> type(TripleApostrophe), popMode;
MLLiteralStringChar : ~[\u0000-\u0008\u000A-\u001F\u007F] -> type(StringChar);
MLLiteralStringNewLine: NL { setText(System.lineSeparator()); } -> type(NewLine);

MLLiteralStringError : . -> type(Error), popMode;


mode DateMode;

Dash : '-';
Plus : '+';
Colon : ':';
DateDot : '.' -> type(Dot);
Z : 'Z';
TimeDelimiter : [Tt] | (' ' { _input.LA(1) >= '0' && _input.LA(1) <= '9' }?);
DateDigits : Digit+;

DateWS : WSChar+ -> type(WS), channel(WHITESPACE), popMode;
DateComment : COMMENT -> type(Comment), channel(COMMENTS), popMode;
DateNewLine: NL { setText(System.lineSeparator()); } -> type(NewLine), popMode;
DateComma: ',' -> type(Comma), popMode;
DateError : . -> type(Error), popMode;


mode InlineTableMode;

InlineTableEnd : '}' { popArrayDepth(); } -> popMode;
InlineTableDot : '.' -> type(Dot);
InlineTableEquals : '=' -> type(Equals), pushMode(ValueMode);
InlineTableComma : ',' -> type(Comma);
InlineTableQuotationMark : '"' -> type(QuotationMark), pushMode(BasicStringMode);
InlineTableApostrophe : '\'' -> type(Apostrophe), pushMode(LiteralStringMode);
InlineTableUnquotedKey : UNQUOTED_KEY -> type(UnquotedKey);

InlineTableWS : WSChar+ -> type(WS), channel(WHITESPACE);
InlineTableComment : COMMENT -> type(Comment), channel(COMMENTS), popMode;
InlineTableNewLine : NL { setText(System.lineSeparator()); } -> type(NewLine), popMode;
InlineTableError : . -> type(Error), popMode;
