package com.winvector.lp;

/**
 * Copyright John Mount, Nina Zumel 2002,2003.  An undisclosed work, all right reserved.
 */



import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;

import org.junit.Test;

import com.winvector.linagl.ColumnMatrix;
import com.winvector.linagl.DenseVec;
import com.winvector.linagl.LinalgFactory;
import com.winvector.linagl.Matrix;
import com.winvector.linalg.colt.ColtMatrix;
import com.winvector.linalg.colt.NativeMatrix;
import com.winvector.lp.impl.RevisedSimplexSolver;

/**
 * Junit tests. run with java junit.swingui.TestRunner
 */
public final class TestLP  {
	
	public <Z extends Matrix<Z>> void testLPSolverTrivial(final LinalgFactory<Z> factory) {
		boolean caught1 = false;
		try {
			final double[] c = new double[1];
			c[0] = -1.0;
			final LPEQProb prob = new LPEQProb(new ColumnMatrix(factory.newMatrix(1, 1,false)),
					new double[1], new DenseVec(c));
			final RevisedSimplexSolver solver = new RevisedSimplexSolver();
			solver.solve(prob, null, 0.0, 1000, factory);
		} catch (LPException.LPUnboundedException ue) {
			caught1 = true;
		} catch (LPException le) {
			assertTrue("caught: " + le,false);
		}
		if (!caught1) {
			assertTrue("didn't detect unbounded case",false);
		}
		try {
			final double[] c = new double[1];
			c[0] = 1.0;
			final LPEQProb prob = new LPEQProb(new ColumnMatrix(factory.newMatrix(1, 1,false)),
					new double[1], new DenseVec(c));
			final RevisedSimplexSolver solver = new RevisedSimplexSolver();
			solver.solve(prob, null, 0.0, 1000, factory);
		} catch (LPException le) {
			fail("caught: " + le);
		}
		
	}
	
	public static <Z extends Matrix<Z>> LPEQProb exampleProblem(final LinalgFactory<Z> factory) throws LPException {
		// p. 320 of Strang exercise 8.2.8
		final Matrix<Z> m = factory.newMatrix(3,5,false);
		final double[] b = new double[3];
		final double[] c = new double[5];
		m.set(0,0,1.0); m.set(0,1,1.0); m.set(0,2,-1.0); b[0] = 4.0;   // x1 + x2 - s1 = 4
		m.set(1,0,1.0); m.set(1,1,3.0); m.set(1,3,-1.0); b[1] = 12.0;  // x1 + 3*x2 - s2 = 12
		m.set(2,0,1.0); m.set(2,1,-1.0); m.set(2,4,-1.0);                // x1 - x2 - s3 = 0
		c[0] = 2.0; c[1] = 1.0;                                     // minimize 2*x1 + x2
		final LPEQProb prob = new LPEQProb(new ColumnMatrix(m),b,new DenseVec(c));
		return prob;
	}
	
	public <Z extends Matrix<Z>> void testLPExample(final LinalgFactory<Z> factory) throws LPException {
		final LPEQProb prob = exampleProblem(factory);
		final LPSoln soln1 = prob.solveDebug(new RevisedSimplexSolver(), 1.0e-6, 1000, factory);
		final double[] expect = {3.00000, 3.00000, 2.00000, 0.00000, 0.00000};
		assertNotNull(soln1);
		assertNotNull(soln1.primalSolution);
		for(int i=0;i<expect.length;++i) {
			assertTrue(Math.abs(soln1.primalSolution.get(i)-expect[i])<1.0e-3);
		}
	}
	

	
	@Test
	public <Z extends Matrix<Z>> void testLPSolverTrivial() throws LPException {
		final ArrayList<LinalgFactory<?>> factories = new ArrayList<LinalgFactory<?>>();
		factories.add(NativeMatrix.factory);
		factories.add(ColtMatrix.factory);
		for(final LinalgFactory<?> f: factories) {
			testLPSolverTrivial(f);
			testLPExample(f);
		}
	}
	
	@Test
	public void testShadow() throws LPException {
		final Matrix<?> m = ColtMatrix.factory.newMatrix(4,3,true);
		final double[] b = new double[4];
		final double[] c = new double[3];
		m.set(0,0,1.0); b[0] = 10.0;   // x0 <= 10
		m.set(1,1,1.0); b[1] = 5.0;   // x1 <=  10
		m.set(2,2,1.0); b[2] = 3.0;   // x2 <= 10
		m.set(3,0,1.0); m.set(3,1,1.0); m.set(3,2,1.0); b[3] = 10.0;   // x0 + x1 + x2 <= 10
		c[0] = -10.0; c[1] = -50.0; c[2] = -100.0;                   // maximize 10*x0 + 50*x1 + 100*x2
		final LPEQProb prob = new LPINEQProb(new ColumnMatrix(m),b,new DenseVec(c)).eqForm();
		//prob.printCPLEX(System.out);
		final RevisedSimplexSolver solver = new RevisedSimplexSolver();
		final double tol = 1.0e-10;
		final LPSoln soln = solver.solve(prob, null, tol, 1000, NativeMatrix.factory);
		final double[] dual = prob.dualSolution(soln, tol,NativeMatrix.factory);
		assertNotNull(dual);
	}
}