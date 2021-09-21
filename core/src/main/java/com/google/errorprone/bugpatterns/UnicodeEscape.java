/*
 * Copyright 2021 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.bugpatterns;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Description.NO_MATCH;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.CompilationUnitTreeMatcher;
import com.google.errorprone.fixes.FixedPosition;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.UnicodeReader;

/** Replaces printable ASCII unicode escapes with the literal version. */
@BugPattern(
    name = "UnicodeEscape",
    summary =
        "Using unicode escape sequences for printable ASCII characters is obfuscated, and"
            + " potentially dangerous.",
    severity = ERROR)
public final class UnicodeEscape extends BugChecker implements CompilationUnitTreeMatcher {
  @Override
  public Description matchCompilationUnit(CompilationUnitTree tree, VisitorState state) {
    new UnicodeScanner(state.getSourceCode().toString(), state).scan();
    return NO_MATCH;
  }

  private final class UnicodeScanner extends UnicodeReader {
    private final String source;
    private final VisitorState state;

    private UnicodeScanner(String source, VisitorState state) {
      super(ScannerFactory.instance(state.context), source.toCharArray(), source.length());
      this.source = source;
      this.state = state;
    }

    public void scan() {
      do {
        if (isUnicode()) {
          if (isBanned(ch)) {
            int startPos = bp;
            while (source.charAt(startPos) != '\\') {
              startPos--;
            }
            state.reportMatch(
                describeMatch(
                    new FixedPosition(state.getPath().getCompilationUnit(), bp),
                    SuggestedFix.replace(startPos, bp + 1, Character.toString(ch))));
          }
        }
        // There's a weird dance in the compiler between UnicodeReader and JavaTokenizer. Unicode
        // parsing happens in the former, but escapes are handled in the latter.
        if (ch == '\\' && peekChar() == '\\') {
          skipChar();
          putChar('\\', true);
        } else {
          scanChar();
        }
      } while (bp < buflen);
    }
  }

  private static boolean isBanned(int c) {
    return (c >= 0x20 && c <= 0x7E) || c == 0xA || c == 0xD;
  }
}
