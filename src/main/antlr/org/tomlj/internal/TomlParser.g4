parser grammar TomlParser;

options { tokenVocab=TomlLexer; }

@header {
package org.tomlj.internal;

import org.tomlj.TomlParseOptions;
}

@members {
  // The maximum number of tables and arrays, not counting the root table, that may enclose any value, table or array
  // in a document. Nesting is limited so that the stack depth needed by the recursive descent parser, the visitors
  // that build the model and the serializers stays bounded, whatever the input. Defaults to
  // TomlParseOptions.DEFAULT_MAX_NESTING_DEPTH and is changed with setMaxNestingDepth(int).
  private int maxNestingDepth = TomlParseOptions.DEFAULT_MAX_NESTING_DEPTH;

  /**
   * Set the maximum number of tables and arrays, not counting the root table, that may enclose any value, table or
   * array in the document.
   *
   * @param maxNestingDepth The maximum nesting depth.
   */
  public void setMaxNestingDepth(int maxNestingDepth) {
    this.maxNestingDepth = maxNestingDepth;
  }

  /** Thrown by the parser when a value is nested deeper than the parser's maximum nesting depth. */
  public static final class NestingTooDeepException extends InputMismatchException {
    private final int maxNestingDepth;

    NestingTooDeepException(TomlParser parser) {
      super(parser);
      this.maxNestingDepth = parser.maxNestingDepth;
    }

    @Override
    public String getMessage() {
      return nestingTooDeepMessage(maxNestingDepth);
    }
  }

  public static String nestingTooDeepMessage(int maxNestingDepth) {
    return "Nesting is too deep (more than " + maxNestingDepth + " level" + (maxNestingDepth == 1 ? "" : "s")
        + " of tables and arrays)";
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
    if (depth > maxNestingDepth) {
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
// Each line is an optional expression, so every decision here needs only one token of lookahead. That lets
// LineRecoveryStrategy skip the rest of a line that cannot be parsed, rather than a failed prediction discarding
// the rest of the document.
toml : expression? (NewLine expression?)* EOF;

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
basicUnescaped : StringChars;

escaped : EscapeSequence;


// Multiline Basic String
mlBasicString : TripleQuotationMark mlBasicChar* TripleQuotationMark;
mlBasicChar
  : mlBasicUnescaped
  | escaped;
mlBasicUnescaped : StringChars;


// Literal String
literalString : Apostrophe literalBody Apostrophe;
literalBody : StringChars*;


// Multiline Literal String
mlLiteralString : TripleApostrophe mlLiteralBody TripleApostrophe;
mlLiteralBody : StringChars*;


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
