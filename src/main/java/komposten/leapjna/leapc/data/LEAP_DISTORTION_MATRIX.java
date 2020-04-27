/*
 * Copyright 2020 Jakob Hjelm (Komposten)
 *
 * This file is part of LeapJna.
 *
 * LeapJna is a free Java library: you can use, redistribute it and/or modify
 * it under the terms of the MIT license as written in the LICENSE file in the root
 * of this project.
 */
package komposten.leapjna.leapc.data;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;


/**
 * <p>
 * A matrix containing lens distortion correction coordinates.
 * </p>
 * <p>
 * Each point in the grid contains the coordinates of the pixel in the image buffer that
 * contains the data for the pixel in the undistorted image corresponding to that point in
 * the grid. Interpolate between points in the matrix to correct image pixels that don't
 * fall directly underneath a point in the distortion grid.
 * </p>
 * <p>
 * Current devices use a 64x64 point distortion grid.
 * </p>
 * 
 * @see <a href=
 *      "https://developer.leapmotion.com/documentation/v4/group___structs.html#struct_l_e_a_p___d_i_s_t_o_r_t_i_o_n___m_a_t_r_i_x">LeapC
 *      API - LEAP_DISTORTION_MATRIX</a>
 */
@FieldOrder({ "matrix" })
public class LEAP_DISTORTION_MATRIX extends Structure
{
	public static final int LEAP_DISTORTION_MATRIX_N = 64;

	/**
	 * A grid of 2D points. Each pair of float values corresponds to the x and y coordinates
	 * of a point. The points are organised into a grid of {@link #LEAP_DISTORTION_MATRIX_N}
	 * rows and columns.
	 */
	public float[] matrix;

	public LEAP_DISTORTION_MATRIX()
	{
		this(null);
	}


	public LEAP_DISTORTION_MATRIX(Pointer pointer)
	{
		super(pointer, ALIGN_NONE);
		matrix = new float[LEAP_DISTORTION_MATRIX_N * LEAP_DISTORTION_MATRIX_N * 2];
		calculateSize(true);
		read();
	}
}
