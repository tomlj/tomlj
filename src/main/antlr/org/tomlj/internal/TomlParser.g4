parser grammar TomlParser;

options { tokenVocab=TomlLexer; }

@header {
package org.tomlj.internal;
}

@members {
  /**
   * The maximum number of tables and arrays, not counting the root table, that may enclose any value, table or array
   * in a document. Nesting is limited so that the stack depth needed by the recursive descent parser, the visitors
   * that build the model and the serializers stays bounded, whatever the input.
   */
  public static final int MAX_NESTING_DEPTH = 128;

  /** Thrown by the parser when a value is nested deeper than {@link #MAX_NESTING_DEPTH}. */
  public static final class NestingTooDeepException extends InputMismatchException {
    NestingTooDeepException(TomlParser parser) {
      super(parser);
    }

    @Override
    public String getMessage() {
      return nestingTooDeepMessage();
    }
  }

  public static String nestingTooDeepMessage() {
    return "Nesting is too deep (more than " + MAX_NESTING_DEPTH + " levels of tables and arrays)";
  }

  // Called before each value. Counts the tables and arrays between the value and the table holding the current
  // expression (each key of a dotted key adds a table and each array adds a level, while the final key of the
  // expression names the value itself), records the deepest count seen on the expression's key/value pair so that
  // LineVisitor can add the depth of the current table, and rejects the value once the count exceeds the limit. The
  // rejected value is skipped whole so that parsing resumes after it with a single error reported.
  private void checkNestingDepth() {
    int depth = -1;
    KeyvalContext outermost = null;
    for (RuleContext ctx = _ctx; ctx != null; ctx = ctx.parent) {
      if (ctx instanceof ArrayContext) {
        depth++;
      } else if (ctx instanceof KeyvalContext) {
        outermost = (KeyvalContext) ctx;
        KeyContext key = outermost.key();
        if (key != null) {
          depth += key.simpleKey().size();
        }
      }
    }
    if (outermost != null && depth > outermost.nesting) {
      outermost.nesting = depth;
    }
    if (depth > MAX_NESTING_DEPTH) {
      NestingTooDeepException e = new NestingTooDeepException(this);
      skipValue();
      throw e;
    }
  }

  // Consumes the tokens of the value about to be parsed: a single token, or an array or inline table together with
  // everything nested inside it.
  private void skipValue() {
    int open = 0;
    do {
      int type = _input.LA(1);
      if (type == Token.EOF) {
        return;
      }
      if (type == ArrayStart || type == InlineTableStart) {
        open++;
      } else if (type == ArrayEnd || type == InlineTableEnd) {
        open--;
      }
      _input.consume();
    } while (open > 0);
  }
}

// Document parser
toml : NewLine* (expression (NewLine+ expression)* NewLine*)? EOF;

expression
  : keyval
  | table
  ;


// Key string parser
tomlKey : key EOF;


// Key-Value pairs
keyval locals [int nesting] : key Equals val;

key : simpleKey (Dot simpleKey)*;
simpleKey
  : quotedKey
  | unquotedKey
  ;

unquotedKey : UnquotedKey;
quotedKey
  : basicString
  | literalString
  ;

val
  : {checkNestingDepth();}
    ( string
    | integer
    | floatValue
    | booleanValue
    | dateTime
    | array
    | inlineTable
    )
  ;


// String
string
  : mlBasicString
  | basicString
  | mlLiteralString
  | literalString
  ;


// Basic String
basicString : QuotationMark basicChar* QuotationMark;
basicChar
  : basicUnescaped
  | escaped
  ;
basicUnescaped : StringChar;

escaped : EscapeSequence;


// Multiline Basic String
mlBasicString : TripleQuotationMark mlBasicChar* TripleQuotationMark;
mlBasicChar
  : mlBasicUnescaped
  | escaped;
mlBasicUnescaped : StringChar;


// Literal String
literalString : Apostrophe literalBody Apostrophe;
literalBody : StringChar*;


// Multiline Literal String
mlLiteralString : TripleApostrophe mlLiteralBody TripleApostrophe;
mlLiteralBody : StringChar*;


// Integer
integer
     : decInt
     | hexInt
     | octInt
     | binInt
     ;

decInt : DecimalInteger;
hexInt : HexInteger;
octInt : OctalInteger;
binInt : BinaryInteger;


// Float
floatValue
  : regularFloat
  | regularFloatInf
  | regularFloatNaN
  ;
regularFloat : FloatingPoint;
regularFloatInf : FloatingPointInf;
regularFloatNaN : FloatingPointNaN;


// Boolean
booleanValue
  : trueBool
  | falseBool
  ;

trueBool : TrueBoolean;
falseBool : FalseBoolean;


// Date and Time
dateTime
 : offsetDateTime
 | localDateTime
 | localDate
 | localTime
 ;

offsetDateTime : date TimeDelimiter time timeOffset;
localDateTime : date TimeDelimiter time;
localDate : date;
localTime : time;

date : year Dash month Dash day;
time : hour Colon minute (Colon second (Dot secondFraction)?)?;
timeOffset
  : Z
  | hourOffset Colon minuteOffset
  ;
hourOffset : (Dash | Plus) hour;
minuteOffset : DateDigits;
secondFraction : DateDigits;
year : DateDigits;
month : DateDigits;
day : DateDigits;
hour : DateDigits;
minute : DateDigits;
second : DateDigits;


// Array
array : ArrayStart (arrayValues NewLine* Comma?)? NewLine* ArrayEnd;
arrayValues : arrayValue (NewLine* Comma arrayValue)*;
arrayValue : NewLine* val;


// Table
table
  : standardTable
  | arrayTable
  ;


// Standard Table
standardTable : TableKeyStart key? TableKeyEnd;


// Inline Table
inlineTable : InlineTableStart (inlineTableValues NewLine* Comma?)? NewLine* InlineTableEnd;
inlineTableValues : inlineTableValue (NewLine* Comma inlineTableValue)*;
inlineTableValue : NewLine* keyval;


// Array Table
arrayTable : ArrayTableKeyStart key? ArrayTableKeyEnd;
