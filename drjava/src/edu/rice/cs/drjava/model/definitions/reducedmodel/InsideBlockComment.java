/*BEGIN_COPYRIGHT_BLOCK
 *
 * Copyright (c) 2001-2017, JavaPLT group at Rice University (drjava@rice.edu)
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the names of DrJava, the JavaPLT group, Rice University, nor the
 *      names of its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software is Open Source Initiative approved Open Source Software.
 * Open Source Initative Approved is a trademark of the Open Source Initiative.
 * 
 * This file is part of DrJava.  Download the current version of this project
 * from http://www.drjava.org/ or http://sourceforge.net/projects/drjava/
 * 
 * END_COPYRIGHT_BLOCK*/

package edu.rice.cs.drjava.model.definitions.reducedmodel;

import static edu.rice.cs.drjava.model.definitions.reducedmodel.ReducedModelStates.*;

/** The shadowing state that corresponds to being inside a block comment.
  * @version $Id$
  */
public class InsideBlockComment extends ReducedModelState {
  /** Singleton instance */
  public static final InsideBlockComment ONLY = new InsideBlockComment();
  /** Singleton constructor */
  private InsideBlockComment() { }
  
  /** Walk function for inside block comment.  Mutually recursive with other walk functions.
    * <ol>
    *  <li> If we've reached the end of the list, return.
    *  <li> If we find * /, combine it into a single Brace, and keep the cursor on that Brace.
    *  <li> If we find // or /*, split that into two Braces and keep the cursor on the first one.
    *  <li> If current brace = * /, mark the current brace as FREE, go to the next brace, and call updateFree.<BR>
    *       Else, mark current brace as INSIDE_BLOCK_COMMENT and go to next brace and recur.
    *  </ol>
    */
  ReducedModelState update(TokenList.Iterator copyCursor) {
    if (copyCursor.atEnd()) return STUTTER;
    _combineCurrentAndNextIfFind("*", "/", copyCursor);
    _combineCurrentAndNextIfFind("*","//", copyCursor);
    _combineCurrentAndNextIfFind("*","/*", copyCursor);
    _combineCurrentAndNextIfFind("","", copyCursor);    
    _combineCurrentAndNextIfEscape(copyCursor);                                              
        
    copyCursor._splitCurrentIfCommentBlock(false, false);
    
    String type = copyCursor.current().getType();
    if (type.equals("*/")) {
      copyCursor.current().setState(FREE);
      copyCursor.next();
      return FREE;
    }
    
    else {
      copyCursor.current().setState(INSIDE_BLOCK_COMMENT);
      copyCursor.next();
      return INSIDE_BLOCK_COMMENT;
    }
  }
}
