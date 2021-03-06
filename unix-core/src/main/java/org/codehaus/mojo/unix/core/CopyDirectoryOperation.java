package org.codehaus.mojo.unix.core;

/*
 * The MIT License
 *
 * Copyright 2009 The Codehaus.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import fj.*;
import fj.data.*;
import org.apache.commons.vfs.*;
import org.codehaus.mojo.unix.*;
import static org.codehaus.mojo.unix.core.AssemblyOperationUtil.*;
import static org.codehaus.mojo.unix.io.IncludeExcludeFilter.*;
import org.codehaus.mojo.unix.util.*;
import static org.codehaus.mojo.unix.util.RelativePath.*;
import org.codehaus.mojo.unix.util.line.*;
import org.codehaus.mojo.unix.util.vfs.*;
import static org.codehaus.mojo.unix.util.vfs.VfsUtil.*;

import java.io.*;
import java.util.regex.*;

/**
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 */
public class CopyDirectoryOperation
    implements AssemblyOperation
{
    private final FileObject from;

    private final RelativePath to;

    private final List<String> includes;

    private final List<String> excludes;

    private final Option<P2<String, String>> pattern;

    private final FileAttributes fileAttributes;

    private final FileAttributes directoryAttributes;

    public CopyDirectoryOperation( FileObject from, RelativePath to, List<String> includes, List<String> excludes,
                                   Option<P2<String, String>> pattern, FileAttributes fileAttributes,
                                   FileAttributes directoryAttributes )
    {
        this.from = from;
        this.to = to;
        this.includes = includes;
        this.excludes = excludes;
        this.pattern = pattern;
        this.fileAttributes = fileAttributes;
        this.directoryAttributes = directoryAttributes;
    }

    public void perform( FileCollector fileCollector )
        throws IOException
    {
        Pattern pattern = this.pattern.isSome() ? Pattern.compile( this.pattern.some()._1() ) : null;
        String replacement = this.pattern.isSome() ? this.pattern.some()._2() : null;

        IncludeExcludeFileSelector selector = new IncludeExcludeFileSelector( from.getName(), includeExcludeFilter().
            addStringIncludes( includes ).
            addStringExcludes( excludes ).
            create() );

        java.util.List<FileObject> files = new java.util.ArrayList<FileObject>();
        from.findFiles( selector, true, files );

        for ( FileObject f : files )
        {
            if ( f.getName().getBaseName().equals( "" ) )
            {
                continue;
            }

            String relativeName = from.getName().getRelativeName( f.getName() );

            // Transform the path if the pattern is set. The input path will always have a leading slash
            // to make it possible to write more natural expressions.
            // With this one can write "/server-1.0.0/(.*)" => $1
            if ( pattern != null )
            {
                String path = relativePath( relativeName ).asAbsolutePath( "/" );

                relativeName = pattern.matcher( path ).replaceAll( replacement );
            }

            RelativePath targetName = to.add( relativeName );

            if ( f.getType() == FileType.FILE )
            {
                fileCollector.addFile( f, AssemblyOperationUtil.fromFileObject( targetName, f, fileAttributes ) );
            }
            else if ( f.getType() == FileType.FOLDER )
            {
                fileCollector.addDirectory(
                    AssemblyOperationUtil.dirFromFileObject( targetName, f, directoryAttributes ) );
            }
        }
    }

    public void streamTo( LineStreamWriter streamWriter )
    {
        streamWriter.add( "Copy directory:" ).
            add( " From: " + asFile( from ).getAbsolutePath() ).
            add( " To: " + to );

        streamIncludesAndExcludes( streamWriter, includes, excludes );

        streamWriter.add( pattern.map( new F<P2<String, String>, String>()
        {
            public String f( P2<String, String> pattern )
            {
                return " Pattern: " + pattern._1() + ", replacement: " + pattern._2();
            }
        } ).orSome( " Pattern: not set" ) );

        streamWriter.add( " Attributes:" ).
            add( " File     : " + fileAttributes ).
            add( " Directory: " + directoryAttributes );
    }
}
