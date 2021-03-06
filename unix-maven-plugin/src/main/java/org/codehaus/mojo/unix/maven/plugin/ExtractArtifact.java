package org.codehaus.mojo.unix.maven.plugin;

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

import org.apache.commons.vfs.*;
import org.apache.maven.plugin.*;
import org.codehaus.mojo.unix.core.*;

import java.io.*;

/**
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 */
@SuppressWarnings( "UnusedDeclaration" )
public class ExtractArtifact
    extends AbstractFileSetOp
{
    private String artifact;

    public ExtractArtifact()
    {
        super( "extract-artifact" );
    }

    public void setArtifact( String artifact )
    {
        this.artifact = nullIfEmpty(artifact);
    }

    public AssemblyOperation createOperation( CreateOperationContext context )
        throws MojoFailureException, FileSystemException, UnknownArtifactException
    {
        File artifactFile = context.project.artifactMap.validateArtifact( artifact );

        FileSystemManager fsManager = VFS.getManager();
        FileObject archiveObject = fsManager.resolveFile( artifactFile.getAbsolutePath() );
        FileObject archive = fsManager.createFileSystem( archiveObject );

        return createCopyArchiveOperation( archive, context );
    }
}
