lexer grammar TomlLexer;

options { superClass=AbstractTomlLexer; }

channels { COMMENTS, WHITESPACE }

tokens { TripleQuotationMark, TripleApostrophe, StringChars, Comma }

@header {
package org.tomlj.internal;
}

fragment WSChar : [ \t];
fragment NL : '\r'? '\n';
fragment COMMENT : '#' (~[\u0000-\u0008\u000A-\u001F\u007F\uD800-\uDFFF])*;
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
Equals : '=' { resetValueState(); } -> pushMode(ValueMode);
QuotationMark : '"' -> pushMode(BasicStringMode);
Apostrophe : '\'' -> pushMode(LiteralStringMode);
TableKeyStart : '[';
TableKeyEnd : ']';
ArrayTableKeyStart : '[[';
ArrayTableKeyEnd : ']]';
UnquotedKey : UNQUOTED_KEY;

WS : WSChar+ -> channel(WHITESPACE);
Comment : COMMENT -> channel(COMMENTS);
NewLine : NL;
Error : .;

mode TomlKeyMode;

TomlKeyDot : '.' -> type(Dot);
TomlKeyQuotationMark : '"' -> type(QuotationMark), pushMode(BasicStringMode);
TomlKeyApostrophe : '\'' -> type(Apostrophe), pushMode(LiteralStringMode);
TomlKeyUnquotedKey : LENIENT_UNQUOTED_KEY -> type(UnquotedKey);

TomlKeyWS : WSChar+ -> type(WS), channel(WHITESPACE);
TomlKeyError : . -> type(Error);

mode ValueMode;

// Strings
ValueQuotationMark : '"' { pushValueModeIfInArray(); } -> type(QuotationMark), mode(BasicStringMode);
ValueTripleQuotationMark : '"""' NL? { pushValueModeIfInArray(); } -> type(TripleQuotationMark), mode(MLBasicStringMode);
ValueApostrophe : '\'' { pushValueModeIfInArray(); } -> type(Apostrophe), mode(LiteralStringMode);
ValueTripleApostrophe : '\'\'\'' NL? { pushValueModeIfInArray(); } -> type(TripleApostrophe), mode(MLLiteralStringMode);

// Integers, dates and times
// One rule matches the digits that start any of them, as they are told apart only by what follows. Digit+ also matches
// a run with a leading zero, which is no integer but may be a year or an hour; ValueVisitor rejects the ones that are
// left as integers. AbstractTomlLexer.decimalIntegerOrDateStart types the token from the character that follows, and
// says why it reads that character in an action rather than a semantic predicate.
fragment DecInt : [-+]? (Digit | Digit1_9 ('_'? Digit)+);
DecimalInteger : (DecInt | Digit+) { decimalIntegerOrDateStart(); };
HexInteger : '0x' HexDig ('_'? HexDig)* { pushValueModeIfInArray(); } -> popMode;
OctalInteger : '0o' Digit0_7 ('_'? Digit0_7)* { pushValueModeIfInArray(); } -> popMode;
BinaryInteger : '0b' Digit0_1 ('_'? Digit0_1)* { pushValueModeIfInArray(); } -> popMode;

// Float
fragment Exp : [eE] [-+]? Digit ('_'? Digit)*;
fragment Frac : '.' Digit ('_'? Digit)*;
FloatingPoint : DecInt (Exp | Frac Exp?) { pushValueModeIfInArray(); } -> popMode;
FloatingPointInf: [-+]? 'inf' { pushValueModeIfInArray(); } -> popMode;
FloatingPointNaN : [-+]? 'nan' { pushValueModeIfInArray(); } -> popMode;

// Boolean
TrueBoolean : 'true' { pushValueModeIfInArray(); } -> popMode;
FalseBoolean : 'false' { pushValueModeIfInArray(); } -> popMode;

// Array
ArrayStart : '[' { arrayDepth++; };
ArrayEnd : ']' { if (inArray()) { arrayDepth--; pushValueModeIfInArray(); } } -> popMode;

// Table
InlineTableStart : '{' { pushValueModeIfInArray(); pushArrayDepth(); } -> mode(InlineTableMode);

ValueComma : ',' -> type(Comma);
ValueNewLine: NL { valueNewLine(); } -> type(NewLine);
ValueWS : WSChar+ -> type(WS), channel(WHITESPACE);
ValueComment : COMMENT -> type(Comment), channel(COMMENTS);

ValueError : . { valueError(); } -> type(Error);


mode BasicStringMode;

BasicStringEnd : '"' -> type(QuotationMark), popMode;
// A run of characters is a single token, rather than a token and parse tree nodes for each character.
BasicStringUnescaped : ~[\u0000-\u0008\u000A-\u001F"\\\u007F\uD800-\uDFFF]+ -> type(StringChars);
EscapeSequence
  : '\\' ~[\n]
  | '\\x' HexDig HexDig
  | '\\u' HexDig HexDig HexDig HexDig
  | '\\U' HexDig HexDig HexDig HexDig HexDig HexDig HexDig HexDig;

BasicStringNewLine: NL { stringNewLine(); } -> type(NewLine);
BasicStringError : . -> type(Error), popMode;


mode MLBasicStringMode;

MLBasicStringSextEnd : '"""' { _input.LA(1) == '"' && _input.LA(2) == '"' && _input.LA(3) == '"' }? -> type(TripleQuotationMark), popMode;
MLBasicStringEnd : '"""' { _input.LA(1) != '"' }? -> type(TripleQuotationMark), popMode;
// A backslash ending a line continues the string over the newlines and whitespace that follow, so it keeps a token type
// of its own: code that finds the lines of a document by the NewLine type must not mistake it for the end of a line.
MLBasicStringLineEndBackslash : '\\' WSChar* NL (WSChar | NL)* -> channel(WHITESPACE);
MLBasicStringUnescaped : ~[\u0000-\u0008\u000A-\u001F"\\\u007F\uD800-\uDFFF]+ -> type(StringChars);
// A quotation mark is a token of its own, so that a run of characters cannot reach into the closing delimiter.
MLBasicStringQuotationMark : '"' -> type(StringChars);
MLBasicStringEscape :
  ('\\x' HexDig HexDig
  | '\\u' HexDig HexDig HexDig HexDig
  | '\\U' HexDig HexDig HexDig HexDig HexDig HexDig HexDig HexDig
  | '\\' .) -> type(EscapeSequence);
// TOML lets a parser choose the newline in a multi-line string value. Both LF and CRLF become "\n", so a document
// parses to the same values on every platform and whichever line endings the file uses.
MLBasicStringNewLine: NL { setText("\n"); } -> type(StringChars);

MLBasicStringError : . -> type(Error), popMode;


mode LiteralStringMode;

LiteralStringEnd : '\'' -> type(Apostrophe), popMode;
LiteralStringChars : ~[\u0000-\u0008\u000A-\u001F'\u007F\uD800-\uDFFF]+ -> type(StringChars);

LiteralStringNewLine: NL { stringNewLine(); } -> type(NewLine);
LiteralStringError : . -> type(Error), popMode;


mode MLLiteralStringMode;

MLLiteralStringSextEnd : '\'\'\'' { _input.LA(1) == '\'' && _input.LA(2) == '\'' && _input.LA(3) == '\'' }? -> type(TripleApostrophe), popMode;
MLLiteralStringEnd : '\'\'\'' { _input.LA(1) != '\'' }? -> type(TripleApostrophe), popMode;
MLLiteralStringChars : ~[\u0000-\u0008\u000A-\u001F'\u007F\uD800-\uDFFF]+ -> type(StringChars);
MLLiteralStringApostrophe : '\'' -> type(StringChars);
// Newlines are normalized as in a multi-line basic string.
MLLiteralStringNewLine: NL { setText("\n"); } -> type(StringChars);

MLLiteralStringError : . -> type(Error), popMode;


mode DateMode;

Dash : '-';
Plus : '+';
Colon : ':';
DateDot : '.' -> type(Dot);
Z : ('Z' | 'z');
TimeDelimiter : [Tt] | (' ' { _input.LA(1) >= '0' && _input.LA(1) <= '9' }?);
DateDigits : Digit+;

DateWS : WSChar+ -> type(WS), channel(WHITESPACE), popMode;
DateComment : COMMENT -> type(Comment), channel(COMMENTS), popMode;
DateNewLine: NL -> type(NewLine), popMode;
DateComma: ',' -> type(Comma), popMode;
// DateStart pushed ValueMode inside an array, so leave it before closing the array as ArrayEnd does.
DateArrayEnd : ']' { if (inArray()) { popMode(); arrayDepth--; pushValueModeIfInArray(); } } -> type(ArrayEnd), popMode;
// A date directly before the end of an inline table leaves DateMode, then closes the table as InlineTableEnd does.
DateInlineTableEnd : '}' { !_modeStack.isEmpty() && _modeStack.peek() == InlineTableMode }? { popMode(); popArrayDepth(); } -> type(InlineTableEnd), popMode;
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
InlineTableComment : COMMENT -> type(Comment), channel(COMMENTS);
InlineTableNewLine : NL { inlineTableNewLine(); } -> type(NewLine);
InlineTableError : . { inlineTableError(); } -> type(Error);
