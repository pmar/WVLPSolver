package com.winvector.linalg.sparse;


/**
 * immutable vector, pop count can not be taking from length (some zero entries may be stored)
 * @author johnmount
 *
 */
public final class SparseVec extends HVec {
	private static final long serialVersionUID = 1L;
	
	public final int dim;
		
	public SparseVec(final int dim, final int[] indices, final double[] values) {
		super(indices,values);
		this.dim = dim;
		final int nindices = this.indices.length;
		if(nindices>0) {
			if(indices[nindices-1]>=dim) {
				throw new IllegalArgumentException("out of bounds index");
			}
		}
	}
	
	public static SparseVec sparseVec(final int dim, final int coord, final double val) {
		if(val==0) {
			return new SparseVec(dim,new int[0],new double[0]);
		} else {
			if((coord<0)||(coord>dim)) {
				throw new IllegalArgumentException();
			}
			return new SparseVec(dim,new int[] { coord }, new double[] {val});
		}
	}
	

	
	
	SparseVec scale(final double[] scale) {
		final int nindices = indices.length;
		double[] nvalues = new double[nindices];
		for(int ii=0;ii<nindices;++ii) {
			final int i = indices[ii];
			if(null==scale) {
				nvalues[ii] = values[ii];
			} else {
				nvalues[ii] = values[ii]*scale[i];
			}
		}
		return new SparseVec(dim,indices,nvalues); // share indices
	}
	


	public double dot(final double[] x) {
		if(x.length!=dim) {
			throw new IllegalArgumentException();
		}
		return super.dot(x);
	}
	
	public double[] toDense() {
		return toArray(dim);
	}
	
	/**
	 * slow (discouraged)
	 * @param i
	 * @return
	 */
	@Override
	public double get(final int i) {
		if((i<0)||(i>=dim)) {
			throw new ArrayIndexOutOfBoundsException(""+i);
		}
		return super.get(i);
	}
}
