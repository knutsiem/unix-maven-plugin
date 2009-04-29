package org.codehaus.mojo.unix.rpm;

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

import static fj.Bottom.*;
import fj.*;
import static fj.Function.*;
import fj.data.List;
import fj.data.*;
import static fj.data.Option.*;
import org.codehaus.mojo.unix.*;
import static org.codehaus.mojo.unix.PackageFileSystem.*;
import org.codehaus.mojo.unix.java.*;
import static org.codehaus.mojo.unix.FileAttributes.*;
import org.codehaus.mojo.unix.UnixFsObject.*;
import static org.codehaus.mojo.unix.util.RelativePath.*;
import org.codehaus.mojo.unix.util.*;
import org.codehaus.mojo.unix.util.line.*;
import static org.codehaus.mojo.unix.util.line.LineStreamUtil.*;
import org.codehaus.plexus.util.*;
import static org.joda.time.LocalDateTime.*;

import java.io.*;
import java.util.*;

/**
 * @author <a href="mailto:trygvis@codehaus.org">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class SpecFile
    implements LineProducer
{
//    public String groupId;
//
//    public String artifactId;

    public PackageVersion version;

    // Will be generated if not set
    public String name;

    public String summary;

    public String license;

    public String distribution;

    public File icon;

    public String vendor;

    public String url;

    public String group;

    public String packager;

    public List<String> defineStatements = List.nil();

    public List<String> provides = List.nil();

    public List<String> requires = List.nil();

    public List<String> conflicts = List.nil();

    public String prefix;

    public File buildRoot;

    public String description;

    public boolean dump;

    private PackageFileSystem<Object> fileSystem;

    public Option<File> includePre = none();

    public Option<File> includePost = none();

    public Option<File> includePreun = none();

    public Option<File> includePostun = none();

    public void beforeAssembly( Directory defaultDirectory )
    {
        Validate.validateNotNull( defaultDirectory );

        Directory root = UnixFsObject.directory( BASE, fromDateFields( new Date( 0 ) ), EMPTY );

        fileSystem = create( new PlainPackageFileSystemObject( root ),
                             new PlainPackageFileSystemObject( defaultDirectory ) );
    }

    public void addFile( UnixFsObject.RegularFile file )
    {
        fileSystem = fileSystem.addFile( new PlainPackageFileSystemObject( file ) );
    }

    public void addDirectory( UnixFsObject.Directory directory )
    {
        fileSystem = fileSystem.addDirectory( new PlainPackageFileSystemObject( directory ) );
    }

    public void addSymlink( UnixFsObject.Symlink symlink )
    {
        fileSystem = fileSystem.addSymlink( new PlainPackageFileSystemObject( symlink ) );
    }

    public void apply( F2<UnixFsObject, FileAttributes, FileAttributes> f )
    {
        fileSystem = fileSystem.apply( f );
    }

    public void streamTo( LineStreamWriter spec )
    {
        for ( String defineStatement : defineStatements )
        {
            spec.add( "%define " + defineStatement );
        }

        UnixUtil.assertField( "version", version );

        spec.
            add( "Name: " + name ).
            add( "Version: " + getRpmVersion( version ) ).
            add( "Release: " + getRpmRelease( version ).orSome( "1" ) ).
            add( "Summary: " + UnixUtil.getField( "summary", summary ) ).
            add( "License: " + UnixUtil.getField( "license", license ) ).
            addIfNotEmpty( "Distribution: ", distribution ).
            add( "Group: " + UnixUtil.getField( "group", group ) ).
            addIfNotEmpty( "Packager", packager ).
            addAllLines( prefix( provides, "Provides" ) ).
            addAllLines( prefix( requires, "Requires" ) ).
            addAllLines( prefix( conflicts, "Conflicts" ) ).
            add( "BuildRoot: " + UnixUtil.getField( "buildRoot", buildRoot ).getAbsolutePath() ).
            add();

        // The %description tag is required even if it is empty.
        spec.
            add( "%description" ).
            addIf( StringUtils.isNotEmpty( description ), description ).
            add();

        spec.
            add( "%files" ).
            addAllLines( fileSystem.prettify().toList().filter( excludePaths ).map( SpecFile.showUnixFsObject() ) );

        spec.addIf( includePre.isSome() || includePost.isSome() || includePreun.isSome() || includePostun.isSome(), "" );
        if ( includePre.isSome() )
        {
            spec.add( "%pre" );
            spec.add( "%include " + includePre.map( FileF.getAbsolutePath ).some() );
        }
        if ( includePost.isSome() )
        {
            spec.add( "%post" );
            spec.add( "%include " + includePost.map( FileF.getAbsolutePath ).some() );
        }
        if ( includePreun.isSome() )
        {
            spec.add( "%preun" );
            spec.add( "%include " + includePreun.map( FileF.getAbsolutePath ).some() );
        }
        if ( includePostun.isSome() )
        {
            spec.add( "%postun" );
            spec.add( "%include " + includePostun.map( FileF.getAbsolutePath ).some() );
        }

        spec.addIf( dump, "%dump" );
    }

    public static String getRpmVersion( PackageVersion version )
    {
        String rpmVersionString = version.version;

        if ( version.snapshot )
        {
            rpmVersionString += "_" + version.timestamp;
        }

        return rpmVersionString.replace( '-', '_' );
    }

    public static Option<String> getRpmRelease( PackageVersion version )
    {
        return version.revision;
    }

    // -----------------------------------------------------------------------
    //
    // -----------------------------------------------------------------------

    private static <A extends UnixFsObject> F<PackageFileSystemObject<Object>, String> showUnixFsObject()
    {
        return new F<PackageFileSystemObject<Object>, String>()
        {
            public String f( PackageFileSystemObject p2 )
            {
                @SuppressWarnings({"unchecked"}) UnixFsObject<A> unixFsObject = p2.getUnixFsObject();
                Option<FileAttributes> attributes = unixFsObject.attributes;

                String s = "";

                s += unixFsObject.attributes.map( tagsF ).bind( formatTags ).orSome( "" );

                s += "%attr(" +
                    attributes.bind( FileAttributes.modeF ).map( UnixFileMode.showOcalString ).orSome( "-" ) + "," +
                    attributes.bind( FileAttributes.userF ).orSome( "-" ) + "," +
                    attributes.bind( FileAttributes.groupF ).orSome( "-" ) + ") ";

                s += unixFsObject.path.asAbsolutePath( "/" );

                if ( unixFsObject instanceof UnixFsObject.RegularFile || unixFsObject instanceof UnixFsObject.Symlink )
                {
                    return s;
                }
                else if ( unixFsObject instanceof UnixFsObject.Directory )
                {
                    return "%dir " + s;
                }

                throw error( "Unknown type UnixFsObject type: " + unixFsObject );
            }
        };
    }

    private static final F<PackageFileSystemObject<Object>, Boolean> excludePaths = new F<PackageFileSystemObject<Object>, Boolean>()
    {
        public Boolean f( PackageFileSystemObject object )
        {
            return !object.getUnixFsObject().path.isBase();
        }
    };

    private static final F<List<String>, Option<String>> formatTags = new F<List<String>, Option<String>>()
    {
        private final F<String, Boolean> config = curry( StringF.equals, "config" );
        private final F<String, Boolean> noreplace = curry( StringF.equals, "rpm:noreplace" );
        private final F<String, Boolean> missingok = curry( StringF.equals, "rpm:missingok" );
        private final F<String, Boolean> doc = curry( StringF.equals, "doc" );
        private final F<String, Boolean> ghost = curry( StringF.equals, "rpm:ghost" );

        public Option<String> f( List<String> tags )
        {
            if ( tags.find( config ).isSome() )
            {
                return some( "%config " );
            }

            if ( tags.find( noreplace ).isSome() )
            {
                return some( "%config(noreplace) " );
            }

            if ( tags.find( missingok ).isSome() )
            {
                return some( "%config(missingok) " );
            }

            if ( tags.find( doc ).isSome() )
            {
                return some( "%doc " );
            }

            if ( tags.find( ghost ).isSome() )
            {
                return some( "%ghost " );
            }

            return none();
        }
    };
}
