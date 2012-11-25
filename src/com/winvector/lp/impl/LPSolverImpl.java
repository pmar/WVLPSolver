package com.winvector.lp.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.winvector.linagl.Matrix;
import com.winvector.linagl.Vector;
import com.winvector.lp.LPEQProb;
import com.winvector.lp.LPException;
import com.winvector.lp.LPException.LPErrorException;
import com.winvector.lp.LPSoln;
import com.winvector.lp.LPSolver;

/**
 * primal: min c.x: A x = b, x>=0 dual: max y.b: y A <= c y b = y A x <= c x (by
 * y A <=c, x>=0) , so y . b <= c . x at optimial y.b = c.x
 * 
 * only need to directly implement: solve min c.x: A x = b, x>=0, m>n A full row
 * rank, given basis-0 m-vector ( A(basis0) = square matrix of basis0 columns
 * x(basis0) = vector with entries selected by basis0 then x(basis0) =
 * A(basis0)^-1 b, x>=0 and x=0 for non-basis elements)
 */
abstract class LPSolverImpl implements LPSolver {
	public int verbose = 0;
	public double minBasisEpsilon = 1.0e-5;

	/**
	 * from Gilbert String Linear Algebra and its Applications second edition.
	 * Section 8.2 The Simplex Method (pp. 316..323)
	 * 
	 * @param A
	 *            matrix m-row by n-column matrix- full row rank, m <=n
	 * @param b
	 *            m-vector
	 * @param c
	 *            n-vector
	 * @param basis
	 *            m-vector that is a valid starting basis
	 * @throws LPException.LPMalformedException
	 *             if parameters don't match defs
	 */
	public static <Z extends Matrix<Z>> void checkParams(final Matrix<Z> A, final Vector b, final Vector c, final int[] basis)
			throws LPException.LPMalformedException {
		if ((A == null) || (b == null) || (c == null) || (basis == null)
				|| (A.rows() <= 0) || (A.rows() != b.size())
				|| (A.rows() != basis.length) || (A.cols() != c.size())) {
			String problem = "misformed problem";
			if (A == null) {
				problem = problem + " A==null";
			} else {
				if (A.rows() <= 0) {
					problem = problem + " A.rows()<=0";
				}
			}
			if (b == null) {
				problem = problem + " b==null";
			} else {
				if (A != null) {
					if (A.rows() != b.size()) {
						problem = problem + " A.rows()(" + A.rows()
								+ ")!=b.size()(" + b.size() + ")";
					}
				}
			}
			if (c == null) {
				problem = problem + " c==null";
			} else {
				if ((A != null) && (A.rows() > 0)) {
					if (A.cols() != c.size()) {
						problem = problem + " A.cols()(" + A.cols()
								+ ")!=c.size()(" + c.size() + ")";
					}
				}
			}
			if (basis == null) {
				problem = problem + " basis==null";
			} else {
				if (A != null) {
					if (A.rows() != basis.length) {
						problem = problem + " A.rows()(" + A.rows()
								+ ")!=basis.length(" + basis.length + ")";
					}
				}
			}
			throw new LPException.LPMalformedException(problem);
		}
		int m = A.rows();
		int n = A.cols();
		if (m > n) {
			throw new LPException.LPMalformedException("m>n");
		}
		final Set<Integer> seen = new HashSet<Integer>();
		for (int i = 0; i < basis.length; ++i) {
			if ((basis[i] < 0) || (basis[i] >= n)) {
				throw new LPException.LPMalformedException(
						"out of range column in basis");
			}
			Integer key = new Integer(basis[i]);
			if (seen.contains(key)) {
				throw new LPException.LPMalformedException(
						"duplicate column in basis");
			}
		}
	}

	/**
	 * @param ncols
	 *            the number of columns we are dealing with
	 * @param basis
	 *            a list of columns
	 * @return [0..ncols-1] set-minus basis
	 */
	static int[] complementaryColumns(int ncols, int[] basis) {
		final Set<Integer> seen = new HashSet<Integer>();
		if (basis != null) {
			for (int i = 0; i < basis.length; ++i) {
				if ((basis[i] >= 0) && (basis[i] < ncols)) {
					seen.add(new Integer(basis[i]));
				}
			}
		}
		int[] r = new int[ncols - seen.size()];
		int j = 0;
		for (int i = 0; (i < ncols) && (j < r.length); ++i) {
			Integer key = new Integer(i);
			if (!seen.contains(key)) {
				r[j] = i;
				++j;
			}
		}
		return r;
	}

	/**
	 * @param A
	 *            full row-rank m by n matrix
	 * @param basis
	 *            column basis
	 * @param hint
	 *            (optional) n-vector
	 * @return basic solution to A x = b x>=0 or null (from basis)
	 */
	private static <Z extends Matrix<Z>> LPSoln<Z> tryBasis(final Matrix<Z> A, final int[] basis, final Vector b) {
		if ((basis == null) || (basis.length != A.rows())) {
			return null;
		}
		Vector x = null;
		try {
			Matrix<Z> AP = A.extractColumns(basis);
			x = AP.solve(b, false);
		} catch (Exception e) {
		}
		if (x == null) {
			return null;
		}
		int j = -1;
		while ((j = x.nextIndex(j)) >= 0) {
			if (x.get(j) < 0) {
				return null;
			}
		}
		return new LPSoln<Z>(x, basis);
	}

	/**
	 * @param A
	 *            full row-rank m by n matrix
	 * @param b
	 *            m-vector
	 * @param hint
	 *            (optional) n-vector
	 * @return basic solution to A x = b x>=0
	 */
	private <Z extends Matrix<Z>> LPSoln<Z> basisFromVector(final Matrix<Z> A, final Vector hint, final Vector b) {
		if (A.cols() <= 0) {
			return new LPSoln<Z>(A.newVector(0), new int[0]);
		}
		int k = 0;
		int[] cb = null;
		if (hint != null) {
			k = hint.nNonZero();
			cb = new int[k];
			int i = 0;
			int j = -1;
			while ((j = hint.nextIndex(j)) >= 0) {
				cb[i] = j;
				++i;
			}
		} else {
			cb = new int[0];
		}
		final int[] gb = A.colBasis(cb,minBasisEpsilon);
		final LPSoln<Z> r = tryBasis(A, gb, b);
		return r;
	}

	/**
	 * @param A
	 *            full row-rank m by n matrix
	 * @param b
	 *            m-vector
	 * @param hint
	 *            (optional) n-vector
	 * @return basic solution to A x = b x>=0
	 */
	private <Z extends Matrix<Z>> LPSoln<Z> inspectForBasis(final Matrix<Z> A, final Vector hint, final Vector b) {
		LPSoln<Z> r = basisFromVector(A, hint, b);
		if (r != null) {
			return r;
		}
		if ((hint != null) && (hint.nNonZero() > 0)) {
			// try zero/initial basis
			r = basisFromVector(A, null, b);
			if (r != null) {
				return r;
			}
		}
		return null;
	}

	static String stringBasis(final int[] b) {
		if (b == null) {
			return null;
		}
		StringBuilder r = new StringBuilder();
		r.append('[');
		for (int i = 0; i < b.length; ++i) {
			if (i > 0) {
				r.append(' ');
			}
			r.append(b[i]);
		}
		r.append(']');
		return r.toString();
	}

	/**
	 * find: min c.x: A x = b, x>=0
	 * 
	 * @param prob
	 *            valid LPProb with full row rank
	 * @param basis0
	 *            m-vector that is a valid starting basis
	 * @param l
	 *            (optional) lower bound on desired solution. ( A(basis0) =
	 *            square matrix of basis0 columns x(basis0) = vector with
	 *            entries selected by basis0 then x(basis0) = A(basis0)^-1 b,
	 *            x>=0 and x=0 for non-basis elements) sorted basis0[i+1] >
	 *            basis0[i]
	 * @return optimal basis (need not be sorted)
	 * @throws LPException
	 *             (if infeas or unbounded) no need to check feasibility of
	 *             input or output (check by wrapper)
	 */
	protected abstract <T extends Matrix<T>> LPSoln<T> rawSolve(LPEQProb<T> prob, int[] basis0,
			final double tol, final int maxRounds) throws LPException;

	/**
	 * @param A
	 *            matrix m-row by n-column matrix- full row rank
	 * @param b
	 *            m-vector
	 * @return basis0 m-vector that is a valid starting basis ( A(basis0) =
	 *         square matrix of basis0 columns x(basis0) = vector with entries
	 *         selected by basis0 then x(basis0) = A(basis0)^-1 b, x>=0 and x=0
	 *         for non-basis elements)
	 * @throws LPException
	 *             (if infeas or unbounded)
	 * 
	 * phase 1: min 1.s (A b) (x) = b, x,s>=0 (s) start with x = 0, s = 1.
	 */
	private <T extends Matrix<T>> LPSoln<T> solvePhase1(final Matrix<T> A, final Vector b, final double tol, final int maxRounds) 
			throws LPException {
		{
			final LPSoln<T> r = inspectForBasis(A, null, b);
			if (r != null) {
				return r;
			}
		}
		final int m = A.rows();
		final int n = A.cols();
		final Vector c = b.newVector(n + 1);
		final Matrix<T> AP = A.newMatrix(m, c.size(),A.sparseRep());
		for (int i = 0; i < m; ++i) {
			for(int j=0;j<n;++j) {
				final double aij = A.get(i, j);
				if(0!=aij) {
					AP.set(i, j, aij);
				}
			}
			final double bi = b.get(i);
			if(0!=bi) {
				AP.set(i, n, bi);
			}
		}
		c.set(n, 1.0);
		final LPEQProb<T> p1prob = new LPEQProb<T>(AP, b, c);
		final int[] ibasis0 = new int[] { n };
		final int[] basis0 = AP.colBasis(ibasis0,minBasisEpsilon);
		// A is full row rank coming in (needs to be also even
		// though AP is full row rank by construction we won't
		// be able to move off it as a basis).
		if (basis0.length != m) {
			throw new LPErrorException("bad basis0");
		}
		//p1prob.soln(basis0,tol); // force check that initial basis is good
		LPSoln<T> soln = rawSolve(p1prob, basis0, tol, maxRounds);
		if ((soln == null) || (soln.basis == null)
				|| (soln.basis.length != basis0.length) || (soln.x == null)
				|| (soln.x.size() != c.size())) {
			throw new LPException.LPErrorException(
					"bad basis back from phase1 raw solve");
		}
		// check objective value is zero
		final double v = c.dot(soln.x);
		if (Math.abs(v)>1.0e-5) {
			throw new LPException.LPInfeasibleException("primal infeasible");
		}
		// check basis is good
		if (soln.basis.length > 1) {
			Arrays.sort(soln.basis);
		}
		for (int i = 1; i < soln.basis.length; ++i) {
			if (soln.basis[i] <= soln.basis[i - 1]) {
				throw new LPException.LPErrorException(
						"duplicate column in basis");
			}
		}
		for (int i = 0; i < soln.basis.length; ++i) {
			if ((soln.basis[i] >= n) && (Math.abs(soln.x.get(soln.basis[i]))!=0)) {
				throw new LPException.LPErrorException(
						"non-zero slack variable in phase 1 " + soln.basis[i]);
			}
		}
		if (soln.basis[soln.basis.length - 1] >= n) {
			// must adjust basis to be off slacks
			int nGood = 0;
			for (int i = 0; i < soln.basis.length; ++i) {
				if (soln.basis[i] < n) {
					++nGood;
				}
			}
			final int[] sb = new int[nGood];
			nGood = 0;
			for (int i = 0; i < soln.basis.length; ++i) {
				if (soln.basis[i] < n) {
					sb[nGood] = soln.basis[i];
					++nGood;
				}
			}
			final int[] rowset = new int[n];
			for (int i = 0; i < rowset.length; ++i) {
				rowset[i] = i;
			}
			final int[] nb = A.extractRows(rowset).colBasis(sb,minBasisEpsilon);
			soln = new LPSoln<T>(soln.x, nb);
			// re-check basis facts
			if ((soln.basis == null) || (soln.basis.length != basis0.length)) {
				throw new LPException.LPErrorException(
						"bad basis back from phase1 raw solve");
			}
			if (soln.basis.length > 1) {
				Arrays.sort(soln.basis);
			}
			for (int i = 1; i < soln.basis.length; ++i) {
				if (soln.basis[i] <= soln.basis[i - 1]) {
					throw new LPException.LPErrorException(
							"duplicate column in basis");
				}
			}
			if (soln.basis[soln.basis.length - 1] >= n) {
				throw new LPException.LPErrorException(
						"basis couldn't move off slack");
			}
		}
		return soln;
	}

	/**
	 * @param prob
	 *            well formed LPProb
	 * @param basis_in
	 *            (optional) valid initial basis
	 * @return x n-vector s.t. A x = b and x>=0 and c.x minimized allowed to
	 *         stop if A x = b, x>=0 c.x <=l
	 * @throws LPException
	 *             (if infeas or unbounded)
	 */
	public <T extends Matrix<T>> LPSoln<T> solve(LPEQProb<T> prob, final int[] basis_in, final double tol,final int maxRounds)
			throws LPException {
		if (verbose > 0) {
			System.out.println("solve:");
			if (verbose > 1) {
				prob.print();
			}
		}
		final LPEQProb<T> origProb = prob;
		// get rid of degenerate cases
		//System.out.println("start rb1");
		final int[] rb = prob.A.rowBasis(null,minBasisEpsilon);
		if ((rb == null) || (rb.length <= 0)) {
			//solving 0 x = b
			if (!prob.b.isZero()) {
				throw new LPException.LPInfeasibleException(
						"linear relaxation incosistent");
			}
			for (int i = 0; i < prob.c.size(); ++i) {
				if (prob.c.get(i) < 0) {
					throw new LPException.LPUnboundedException(
							"unbounded minimum solving 0 x = 0");
				}
			}
			final Vector x = prob.b.newVector(prob.A.cols());
			int[] b = new int[x.size()];
			for (int i = 0; i < b.length; ++i) {
				b[i] = i;
			}
			return new LPSoln<T>(x, b);
		}
		// select out irredundant rows
		if (rb.length != prob.A.rows()) {
			final Matrix<T> nA = prob.A.extractRows(rb);
			final Vector nb = prob.b.extract(rb);
			prob = new LPEQProb<T>(nA, nb, prob.c);
		}
		// deal with square system
		if (prob.A.rows() >= prob.A.cols()) {
			final Vector x = prob.A.solve(prob.b, false);
			if (x == null) {
				throw new LPException.LPInfeasibleException(
						"linear problem infeasible");
			}
			LPEQProb.checkPrimFeas(prob.A, prob.b, x, tol);
			if (prob != origProb) {
				LPEQProb.checkPrimFeas(origProb.A, origProb.b, x, tol);
			}
			int[] b = new int[x.size()];
			for (int i = 0; i < b.length; ++i) {
				b[i] = i;
			}
			return new LPSoln<T>(x, b);
		}
		int[] basis0 = null;
		if ((basis_in != null) && (basis_in.length == prob.A.rows())) {
			try {
				if (verbose > 0) {
					System.out.println("import basis");
				}
				basis0 = new int[basis_in.length];
				for (int i = 0; i < basis0.length; ++i) {
					basis0[i] = basis_in[i];
				}
				final Vector x0 = LPEQProb.soln(prob.A, prob.b, basis0, tol);
				LPEQProb.checkPrimFeas(prob.A, prob.b, x0, tol);
			} catch (Exception e) {
				basis0 = null;
				System.out.println("caught: " + e);
			}
		}
		if (basis0 == null) {
			if (verbose > 0) {
				System.out.println("phase1");
			}
			final LPSoln<T> phase1Soln = solvePhase1(prob.A, prob.b, tol,maxRounds);
			if (phase1Soln != null) {
				basis0 = phase1Soln.basis;
			}
		}
		if (verbose > 0) {
			System.out.println("phase2");
		}
		if (prob.c.isZero()) {
			// no objective function, any basis will do
			return tryBasis(prob.A, basis0, prob.b);
		}
		final LPSoln<T> soln = rawSolve(prob, basis0, tol,maxRounds);
		if ((soln == null) || (soln.x == null) || (soln.basis == null)
				|| (soln.basis.length != basis0.length)) {
			throw new LPException.LPErrorException(
					"bad basis back from phase1 raw solve");
		}
		LPEQProb.checkPrimFeas(prob.A, prob.b, soln.x, tol);
		if (prob != origProb) {
			LPEQProb.checkPrimFeas(origProb.A, origProb.b, soln.x, tol);
		}
		return soln;
	}
}