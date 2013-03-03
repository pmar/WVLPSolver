package com.winvector.lp.impl;

import java.util.Random;

import com.winvector.linagl.LinalgFactory;
import com.winvector.linagl.Matrix;
import com.winvector.linagl.SparseVec;
import com.winvector.lp.AbstractLPEQProb;
import com.winvector.lp.EarlyExitCondition;
import com.winvector.lp.InspectionOrder;
import com.winvector.lp.LPEQProb;
import com.winvector.lp.LPException;
import com.winvector.lp.LPException.LPTooManyStepsException;
import com.winvector.lp.LPSoln;

/**
 * basic primal revised simplex method
 * primal: min c.x: A x = b, x>=0
 * based on Strang "Linear Algebra and its Applications" second edition
 * see: http://www.win-vector.com/blog/2012/11/yet-another-java-linear-programming-library/
 * hosted at: https://github.com/WinVector/WVLPSolver
 */
public final class RevisedSimplexSolver extends LPSolverImpl {
	public int debug = 0;
	public double checkTol = 1.0e-8;
	public double enteringTol = 1.0e-5;
	public double leavingTol = 1.0e-7;
	public boolean earlyR = true;                 // allow partial inspection for entering columns
	public boolean resuffle = true;               // re-shuffle inspection order each pass
	private final Random rand = new Random(3252351L);

	

	
	private <T extends Matrix<T>> int[] runSimplex(final EnhancedBasis<T> tab, final double tol, 
			final int maxRounds, final EarlyExitCondition earlyExitCondition) throws LPException {
		if (debug > 0) {
			System.out.println("start: " + stringBasis(tab.basis));
		}
		// run counters
		int normalSteps = 0;
		int inspections = 0;
		final InspectionOrder inspectionOrder = tab.prob.buildOrderTracker(rand);
		final double[] bRatPtr = new double[1];
		double[] b = tab.prob.b();
		while (normalSteps<=maxRounds) {
			++normalSteps;
			//prob.soln(basis,tol);
			//System.out.println("basis good");
			inspectionOrder.startPass();
			if(resuffle) {
				inspectionOrder.shuffle();
			}
			final double[] lambda = tab.leftBasisSoln();
			final double[] preB = tab.basisSolveRight(b);
			for(int i=0;i<preB.length;++i) { // assume any negative are rounding errors
				preB[i] = Math.max(0.0,preB[i]);
			}
			// find most negative entry of r, if any
			// determines joining variable
			int rEnteringV = -1;
			double bestRi = Double.NaN;
			inspectionLoop:
			while(inspectionOrder.hasNext()) {
				++inspections;
				final int v = inspectionOrder.take();
				if(!tab.curBasisSet.contains(v)) {
					final double ri = tab.computeRI(lambda, v);
					//System.out.println("\t" + v + " ri: " + ri);
					if(ri < -enteringTol) {
						if((rEnteringV < 0)||(ri < bestRi)) {
							rEnteringV = v;
							bestRi = ri;
							if(earlyR) {
								inspectionOrder.liked(v);
								break inspectionLoop;
							}
						}
					}
					inspectionOrder.disliked(v);
				}
			}
			final int enteringV = rEnteringV;
			if (enteringV < 0) {
				// no entry, at optimum
				//System.out.println("steps: " + normalSteps + ", inspections: " + inspections + ", ratio: " + (inspections/(double)normalSteps));
				return tab.basis();
			}
			final SparseVec u = tab.prob.extractColumn(enteringV);
			final double[] binvu = tab.basisSolveRight(u);
			final int leavingI = findLeaving(preB,binvu,bRatPtr);
			if (leavingI < 0) {
				throw new LPException.LPUnboundedException(
						"problem unbounded");
			}
			if (debug > 0) {
				System.out.print(" leavingI: " + leavingI);
				if (leavingI >= 0) {
					System.out.print(" var=" + tab.basis[leavingI]);
				}
				System.out.println();
			}
			// perform the swap
			tab.basisPivot(leavingI,enteringV,binvu);
			//System.out.println("leave: " + basis[leavingI]);
			if(null!=earlyExitCondition) {
				if(earlyExitCondition.canExit(tab.basis)) {
					//System.out.println("steps: " + normalSteps + ", inspections: " + inspections + ", ratio: " + (inspections/(double)normalSteps));
					return tab.basis();
				}
			}
		}
		throw new LPTooManyStepsException("max steps>" + maxRounds);
	}

	/**
	 * the idea is the preB and preBprime should be non-negative, being solutions from another basis
	 * @param tol >=0
	 * @param preB
	 * @param binvu
	 * @return
	 */
	private <Z extends Matrix<Z>> int findLeaving(final double[] preB, final double[] binvu, final double[] bestRatPointer) {
		// find least ratio of xB[i] to v[i] where v[i]>0
		// determines joining variable
		//(degenerate when xB[i] = 0, could put anti-cycling code here)
		double determiningRat = Double.NaN;
		int leavingI = -1;
		for(int i=0;i<binvu.length;++i) {
			final double vi = binvu[i];
			//System.out.println("l(" + basis[i] + ")= " + vi);
			if (vi>leavingTol) {
				final double xBi = preB[i];
				final double rat = Math.max(0.0,xBi)/vi;
				if ((leavingI<0)
					|| (determiningRat > rat)) {
					determiningRat = rat;
					leavingI = i;
				}
			}
		}
		if(null!=bestRatPointer) {
			bestRatPointer[0] = determiningRat;
		}
		return leavingI;
	}

	/**
	 * solve: min c.x: A x = b, x>=0
	 * 
	 * @param prob
	 *            well formed LPProb
	 * @param basis0
	 *            m-vector that is a valid starting basis ( A(basis0) = square
	 *            matrix of basis0 columns x(basis0) = vector with entries
	 *            selected by basis0 then x(basis0) = A(basis0)^-1 b, x>=0 and
	 *            x=0 for non-basis elements) sorted basis0[i+1] > basis0[i]
	 *            allowed to stop if A x = b, x>=0 c.x <=l
	 * @return optimal basis (need not be sorted)
	 * @throws LPException
	 *             (if infeas or unbounded)
	 */
	@Override
	protected <T extends Matrix<T>> LPSoln rawSolve(final AbstractLPEQProb prob,
			final int[] basis0, double tol, final int maxRounds, final LinalgFactory<T> factory,
			final EarlyExitCondition earlyExitCondition) throws LPException {
		if ((tol<=0)||Double.isNaN(tol)||Double.isInfinite(tol)) {
			tol = 0.0;
		}
		final EnhancedBasis<T> t = new EnhancedBasis<T>(prob, basis0,factory);
		final int[] rbasis = runSimplex(t,tol,maxRounds,earlyExitCondition);
		//System.out.println("steps: " + t.normalSteps);
		//System.out.println("" + "nvars" + "\t" + "ncond" + "\t" + "steps" + "\t" + "inspections");
		//System.out.println("" + prob.nvars() + "\t" + prob.b.length + "\t" + t.normalSteps + "\t" + t.inspections);
		if (rbasis == null) {
			return null;
		}
		final LPSoln lpSoln = new LPSoln(LPEQProb.primalSoln(prob, rbasis, factory), rbasis, null); // TODO: annotate row basis
		return lpSoln;
	}
}