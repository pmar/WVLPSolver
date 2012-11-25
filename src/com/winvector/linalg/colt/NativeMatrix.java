package com.winvector.linalg.colt;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;

import com.winvector.linagl.Matrix;
import com.winvector.linagl.Vector;

public class NativeMatrix extends Matrix<NativeMatrix> {
	private static final long serialVersionUID = 1L;
	
	private final int rows;
	private final int cols;
	private final double[][] u;
	
	NativeMatrix(final int m, final int n, final boolean wantSparse) {
		this.rows = m;
		this.cols = n;
		u = new double[m][n];
	}
	
	public NativeMatrix(final double[][] u) {
		this.u = u;
		this.rows = u.length;
		this.cols = u.length;
	}

	@Override
	public int cols() {
		return cols;
	}

	@Override
	public int rows() {
		return rows;
	}

	@Override
	public double get(final int row, final int col) {
		return u[row][col];
	}

	@Override
	public void set(final int row, final int col, final double v) {
		u[row][col] = v;
	}

	@Override
	public NativeMatrix newMatrix(final int rows, final int cols, final boolean wantSparse) {
		return new NativeMatrix(rows,cols,wantSparse);
	}

	@Override
	public NativeVector newVector(final int rows) {
		return new NativeVector(rows);
	}
	
	@Override
	public NativeVector solve(final Vector bIn, final boolean leastsq) {
		if(bIn.size()!=rows()) {
			throw new IllegalArgumentException();
		}
		DoubleMatrix2D b = new DenseDoubleMatrix2D(rows(),1);
		for(int i=0;i<rows();++i) {
			b.set(i,0,bIn.get(i));
		}
		DoubleMatrix2D a = new DenseDoubleMatrix2D(u);
		if(leastsq) {
			final DoubleMatrix2D at = Algebra.ZERO.transpose(a);
			a = Algebra.ZERO.mult(at,a);
			b = Algebra.ZERO.mult(at,b);
		}
		final DoubleMatrix2D p = Algebra.ZERO.solve(a,b);
		final double[] r = new double[cols()];
		for(int i=0;i<r.length;++i) {
			r[i] = p.get(i,0);
		}
		return new NativeVector(r);
	}

	@Override
	public NativeMatrix copy() {
		final NativeMatrix r = new NativeMatrix(rows,cols,false);
		for(int i=0;i<rows;++i) {
			for(int j=0;j<cols;++j) {
				r.u[i][j] = u[i][j];
			}
		}
		return r;
	}

	@Override
	public NativeMatrix transpose() {
		final NativeMatrix r = new NativeMatrix(cols,rows,false);
		for(int i=0;i<rows;++i) {
			for(int j=0;j<cols;++j) {
				r.u[j][i] = u[i][j];
			}
		}
		return r;
	}
	
	@Override
	public NativeMatrix inverse() {
		if(rows!=cols) {
			throw new IllegalArgumentException();
		}
		final DoubleMatrix2D ainv = Algebra.ZERO.inverse(new DenseDoubleMatrix2D(u));
		final NativeMatrix r = new NativeMatrix(rows,cols,false);
		for(int i=0;i<rows;++i) {
			for(int j=0;j<cols;++j) {
				r.u[i][j] = ainv.get(i, j);
			}
		}
		return r;
	}
	
	@Override
	public <Z extends NativeMatrix> NativeMatrix multMat(final Z o) {
		if(cols!=o.rows) {
			throw new IllegalArgumentException();
		}
		final NativeMatrix r = new NativeMatrix(rows,o.cols,false);
		for(int i=0;i<rows;++i) {
			for(int j=0;j<o.cols;++j) {
				double v = 0.0;
				for(int k=0;k<cols;++k) {
					v += u[i][k]*o.u[k][j];
				}
				r.u[i][j] = v;
			}
		}
		return r;
	}

	@Override
	public boolean sparseRep() {
		return false;
	}
	
	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder();
		b.append("[" + rows + "][" + cols + "]{\n");
		for(int i=0;i<rows;++i) {
			b.append(" ");
			b.append(NativeVector.toString(u[i]));
			if(i<rows-1) {
				b.append(",");
			}
			b.append("\n");
		}
		b.append("}\n");
		return b.toString();
	}
}

