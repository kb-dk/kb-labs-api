/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.kb;

import java.io.IOException;
import java.io.Writer;

/**
 * Trivial wrapper for {@link Writer} that converts all checked Exceptions to {@link RuntimeException}s.
 */
public class RuntimeWriter extends Writer {

    private final Writer inner;

    public RuntimeWriter(Writer inner) {
        this.inner = inner;
    }

    public static Writer nullWriter() {
        return Writer.nullWriter();
    }

    @Override
    public void write(int c) {
        try {
            inner.write(c);
        } catch (IOException e) {
            throw new RuntimeException("IOException while writing " + c, e);
        }
    }

    @Override
    public void write(char[] cbuf) {
        try {
            inner.write(cbuf);
        } catch (IOException e) {
            throw new RuntimeException("IOException writing to buffer", e);
        }
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        try {
            inner.write(cbuf, off, len);
        } catch (IOException e) {
            throw new RuntimeException("IOException writing to buffer", e);
        }
    }

    @Override
    public void write(String str) {
        try {
            inner.write(str);
        } catch (IOException e) {
            throw new RuntimeException("IOException writing String", e);
        }
    }

    @Override
    public void write(String str, int off, int len) {
        try {
            inner.write(str, off, len);
        } catch (IOException e) {
            throw new RuntimeException("IOException writing String buffer", e);
        }
    }

    @Override
    public Writer append(CharSequence csq) {
        try {
            return inner.append(csq);
        } catch (IOException e) {
            throw new RuntimeException("IOException writing CharSequence", e);
        }
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) {
        try {
            return inner.append(csq, start, end);
        } catch (IOException e) {
            throw new RuntimeException("IOException writing CharSequence", e);
        }
    }

    @Override
    public Writer append(char c) {
        try {
            return inner.append(c);
        } catch (IOException e) {
            throw new RuntimeException("IOException writing char " + c, e);
        }
    }

    @Override
    public void flush() {
        try {
            inner.flush();
        } catch (IOException e) {
            throw new RuntimeException("IOException flushing", e);
        }
    }

    @Override
    public void close() {
        try {
            inner.close();
        } catch (IOException e) {
            throw new RuntimeException("IOException closing", e);
        }
    }
}
