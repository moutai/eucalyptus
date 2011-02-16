/*******************************************************************************
 * Copyright (c) 2009  Eucalyptus Systems, Inc.
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, only version 3 of the License.
 * 
 * 
 *  This file is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 * 
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *  Please contact Eucalyptus Systems, Inc., 130 Castilian
 *  Dr., Goleta, CA 93101 USA or visit <http://www.eucalyptus.com/licenses/>
 *  if you need additional information or have any questions.
 * 
 *  This file may incorporate work covered under the following copyright and
 *  permission notice:
 * 
 *    Software License Agreement (BSD License)
 * 
 *    Copyright (c) 2008, Regents of the University of California
 *    All rights reserved.
 * 
 *    Redistribution and use of this software in source and binary forms, with
 *    or without modification, are permitted provided that the following
 *    conditions are met:
 * 
 *      Redistributions of source code must retain the above copyright notice,
 *      this list of conditions and the following disclaimer.
 * 
 *      Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 * 
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *    IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 *    TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 *    PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 *    OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *    EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *    PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *    PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *    LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *    NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. USERS OF
 *    THIS SOFTWARE ACKNOWLEDGE THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE
 *    LICENSED MATERIAL, COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS
 *    SOFTWARE, AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *    IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA, SANTA
 *    BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY, WHICH IN
 *    THE REGENTS’ DISCRETION MAY INCLUDE, WITHOUT LIMITATION, REPLACEMENT
 *    OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO IDENTIFIED, OR
 *    WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT NEEDED TO COMPLY WITH
 *    ANY SUCH LICENSES OR RIGHTS.
 *******************************************************************************
 * @author chris grzegorczyk <grze@eucalyptus.com>
 */

package com.eucalyptus.binding;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.jibx.binding.Compile;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;


public class CompileBindings extends Task {
  private List<FileSet> classFileSets   = null;
  private List<FileSet> bindingFileSets = null;
  private List<String>  bindings        = null;
  
  @Override
  public void init( ) throws BuildException {
    super.init( );
    this.classFileSets = new ArrayList<FileSet>( );
    this.bindingFileSets = new ArrayList<FileSet>( );
    this.bindings = new ArrayList<String>( );
  }
  
  public void addClassFileSet( FileSet classFiles ) {
    this.classFileSets.add( classFiles );
  }
  
  public void addBindingFileSet( FileSet bindings ) {
    this.bindingFileSets.add( bindings );
  }
  
  private String[] paths( ) {
    Set<String> dirs = new HashSet<String>( );
    for ( FileSet fs : this.classFileSets ) {
      final String dirName = fs.getDir( getProject( ) ).getAbsolutePath( );
      for ( String d : fs.getDirectoryScanner( getProject( ) ).getIncludedFiles( ) ) {
        final String buildDir = dirName + File.separator + d.replaceAll( "build/.*", "build" );
        if ( !dirs.contains( buildDir ) ) {
          log( "Found class directory: " + buildDir );
          dirs.add( buildDir );
        }
      }
    }
    return dirs.toArray( new String[] {} );
  }
  
  private String[] bindings( ) {
    List<String> bindings = new ArrayList<String>( );
    bindings.add( this.getProject( ).getBaseDir( ) + File.separator + "modules/msgs/src/main/resources/msgs-binding.xml" );
    for ( FileSet fs : this.bindingFileSets ) {
      final String dirName = fs.getDir( getProject( ) ).getAbsolutePath( );
      for ( String b : fs.getDirectoryScanner( getProject( ) ).getIncludedFiles( ) ) {
        final String bindingFilePath = dirName + File.separator + b;
        log( "Found binding: " + bindingFilePath );
        if ( !bindingFilePath.endsWith( "msgs-binding.xml" ) ) {
          bindings.add( bindingFilePath );
        }
      }
    }
    return bindings.toArray( new String[] {} );
  }
  
  PrintStream oldOut = System.out, oldErr = System.err;
  public void error( Throwable e ) {
    e.printStackTrace( System.err );
    e.printStackTrace( System.err );
    System.exit( -1 );
  }
  
  public void execute( ) {
    PrintStream buildLog;
    try {
      buildLog = new PrintStream( new FileOutputStream( "bind-compile.log", false ) );
      if ( this.classFileSets.isEmpty( ) ) {
        throw new BuildException( "No classes were provided to bind." );
      } else if ( this.bindingFileSets.isEmpty( ) ) {
        throw new BuildException( "No bindings were provided to bind." );
      } else {
        Path path = new Path( getProject( ) );
        for ( String p : paths( ) ) {
          path.add( new Path( getProject( ), p ) );
        }
        for ( File f : new File( "lib" ).listFiles( new FilenameFilter( ) {
          @Override
          public boolean accept( File dir, String name ) {
            return name.endsWith( ".jar" );
          }
        } ) ) {
          path.add( new Path( getProject( ), f.getAbsolutePath( ) ) );
        }
        runBindingCompiler( );
      }
    } catch ( FileNotFoundException e2 ) {
      this.error( e2 );
    }    
  }
  
  private void runBindingCompiler( ) {
    ClassLoader old = Thread.currentThread( ).getContextClassLoader( );
    ClassLoader cl = this.getUrlClassLoader( );
    try {
      BindingGenerator.MSG_TYPE = cl.loadClass( "edu.ucsb.eucalyptus.msgs.BaseMessage" );
      BindingGenerator.DATA_TYPE = cl.loadClass( "edu.ucsb.eucalyptus.msgs.EucalyptusData" );
      Map<String, Class> classes = new TreeMap<String, Class>( ) {
        {
          put( BindingGenerator.MSG_TYPE.getName( ), BindingGenerator.MSG_TYPE );
          put( BindingGenerator.DATA_TYPE.getName( ), BindingGenerator.DATA_TYPE );
        }
      };
      for ( FileSet fs : this.classFileSets ) {
        for ( String classFileName : fs.getDirectoryScanner( getProject( ) ).getIncludedFiles( ) ) {
          try {
            if ( !classFileName.endsWith( "class" ) ) continue;
            Class c = cl.loadClass( classFileName.replaceFirst( "[^/]*/[^/]*/", "" ).replaceAll( "/", "." ).replaceAll( "\\.class.{0,1}", "" ) );
            System.out.println( "Loaded class: " + c );
            classes.put( c.getName( ), c );
          } catch ( ClassNotFoundException e ) {
            e.printStackTrace( );
          }
        }
      }
      Compile compiler = new Compile( true, true, true, false, false, false );
      compiler.compile( this.pathStrings( ), bindings( ) );
    } catch ( Throwable e ) {
      e.printStackTrace( );
    } finally {
      Thread.currentThread( ).setContextClassLoader( old );
    }
  }
  /**
   * TODO: DOCUMENT CompileBindings.java
   * @return
   */
  private String[] pathStrings( ) {
    List<String> strs = Lists.newArrayList( );
    for( URL u : this.pathUrls( ) ) {
      try {
        strs.add( new File( u.toURI( ) ).getAbsolutePath( ) );
      } catch ( URISyntaxException ex ) {
        error( ex );
      }
    }
    return strs.toArray( new String[] {});
  }

  private ClassLoader getUrlClassLoader( ) {
    ClassLoader cl = URLClassLoader.newInstance( this.pathUrls( ), Thread.currentThread( ).getContextClassLoader( ) );
    return cl;
  }
  private URL[] pathUrls( ) {
    Set<URL> dirUrls = new HashSet<URL>( );
    for ( FileSet fs : this.classFileSets ) {
      final String dirName = fs.getDir( getProject( ) ).getAbsolutePath( );
      for ( String d : fs.getDirectoryScanner( getProject( ) ).getIncludedFiles( ) ) {
        final String buildDir = dirName + File.separator + d.replaceAll( "build/.*", "build" );
        try {
          URL buildDirUrl = new File( buildDir ).toURL( );
          if ( !dirUrls.contains( buildDirUrl ) ) {
            log( "Found class directory: " + buildDirUrl );
            dirUrls.add( buildDirUrl );
          }
        } catch ( MalformedURLException ex ) {
          error( ex );
        }
      }
    }
    for ( File f : new File( this.project.getBaseDir( ).getAbsolutePath( ) + File.separator + "lib" ).listFiles( new FilenameFilter( ) {
      @Override
      public boolean accept( File dir, String name ) {
        return name.endsWith( ".jar" );
      }
    } ) ) {
      try {
        dirUrls.add( f.toURL( ) );
      } catch ( MalformedURLException ex ) {
        error( ex );
      }
    }
    return dirUrls.toArray( new URL[] {} );
  }
    
}