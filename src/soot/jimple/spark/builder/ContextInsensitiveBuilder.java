/* Soot - a J*va Optimization Framework
 * Copyright (C) 2002 Ondrej Lhotak
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package soot.jimple.spark.builder;
import soot.jimple.spark.*;
import soot.jimple.spark.pag.*;
import soot.jimple.toolkits.callgraph.*;
import soot.jimple.toolkits.pointer.util.NativeMethodDriver;
import soot.jimple.toolkits.pointer.util.NativeHelper;
import soot.jimple.toolkits.pointer.DumbPointerAnalysis;
import soot.*;
import java.util.*;
import soot.jimple.*;
import soot.jimple.spark.internal.*;
import soot.jimple.spark.sets.PointsToSetInternal;
import soot.jimple.spark.solver.OnFlyCallGraph;
import soot.util.queue.*;
import soot.options.*;

/** A context insensitive pointer assignment graph builder.
 * @author Ondrej Lhotak
 */
public class ContextInsensitiveBuilder {
    public void preJimplify() {
        for( Iterator cIt = Scene.v().getClasses().iterator(); cIt.hasNext(); ) {
            final SootClass c = (SootClass) cIt.next();
            for( Iterator mIt = c.methodIterator(); mIt.hasNext(); ) {
                final SootMethod m = (SootMethod) mIt.next();
                if( !m.isConcrete() ) continue;
                if( m.isNative() ) continue;
                if( m.isPhantom() ) continue;
                m.retrieveActiveBody();
            }
        }
    }
    /** Creates an empty pointer assignment graph. */
    public AbstractPAG setup( AbstractSparkOptions opts ) {
        if( opts instanceof SparkOptions ) {
            pag = new PAG( (SparkOptions) opts );
        } else if( opts instanceof BDDSparkOptions ) {
            pag = new BDDPAG( (BDDSparkOptions) opts );
        } else throw new RuntimeException();
        if( opts.simulate_natives() ) {
            NativeHelper.register( new SparkNativeHelper( pag ) );
        }
        if( opts.on_fly_cg() && !opts.vta() ) {
            ofcg = new OnFlyCallGraph( (PAG) pag );
            pag.setOnFlyCallGraph( ofcg );
        } else {
            cgb = new CallGraphBuilder( DumbPointerAnalysis.v() );
        }
        return pag;
    }
    /** Fills in the pointer assignment graph returned by setup. */
    public void build() {
        QueueReader callEdges;
        if( ofcg != null ) {
            callEdges = ofcg.callGraph().listener();
            ofcg.build();
            reachables = ofcg.reachableMethods();
            reachables.update();
        } else {
            callEdges = cgb.getCallGraph().listener();
            cgb.build();
            reachables = cgb.reachables();
        }
        for( Iterator cIt = Scene.v().getClasses().iterator(); cIt.hasNext(); ) {
            final SootClass c = (SootClass) cIt.next();
	    handleClass( c );
	}
        Stmt s = null;
        while(true) {
            Edge e = (Edge) callEdges.next();
            if( e == null ) break;
            AbstractMethodPAG.v( pag, e.tgt() ).addToPAG(null);
            pag.addCallTarget( e );
        }

        if( pag.getOpts().verbose() ) {
            G.v().out.println( "Total methods: "+totalMethods );
            G.v().out.println( "Initially reachable methods: "+analyzedMethods );
            G.v().out.println( "Classes with at least one reachable method: "+classes );
        }
    }

    /* End of public methods. */
    /* End of package methods. */
    protected void handleClass( SootClass c ) {
        boolean incedClasses = false;
	Iterator methodsIt = c.methodIterator();
	while( methodsIt.hasNext() ) 
	{
	    SootMethod m = (SootMethod) methodsIt.next();
	    if( !m.isConcrete() && !m.isNative() ) continue;
            totalMethods++;
            if( reachables.contains( m ) ) {
                AbstractMethodPAG mpag = AbstractMethodPAG.v( pag, m );
                mpag.build();
                mpag.addToPAG(null);
                analyzedMethods++;
                if( !incedClasses ) {
                    incedClasses = true;
                    classes++;
                }
            }
	}
    }


    private AbstractPAG pag;
    private CallGraphBuilder cgb;
    private OnFlyCallGraph ofcg;
    private ReachableMethods reachables;
    int classes = 0;
    int totalMethods = 0;
    int analyzedMethods = 0;
    int stmts = 0;
}

