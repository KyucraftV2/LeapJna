package komposten.leapjna.leapc.data;

import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;

import komposten.leapjna.LeapC;
import komposten.leapjna.leapc.enums.eLeapConnectionStatus;


/**
 * <p>
 * Information about a connection.
 * </p>
 * <p>
 * Instances of this struct are created by
 * {@link LeapC#LeapGetConnectionInfo(Pointer, LEAP_CONNECTION_INFO)}.
 * </p>
 * 
 * @see <a href=
 *      "https://developer.leapmotion.com/documentation/v4/group___structs.html#struct_l_e_a_p___c_o_n_n_e_c_t_i_o_n___i_n_f_o">LeapC
 *      API - LEAP_CONNECTION_INFO</a>
 */
@FieldOrder({ "size", "status" })
public class LEAP_CONNECTION_INFO extends Structure
{
	/** The size of this structure. */
	public int size;

	/**
	 * <p>
	 * The current status of this connection. Use {@link #getStatus()} to get the status as
	 * a {@link eLeapConnectionStatus} value.
	 * </p>
	 * <p>
	 * <b>Note</b>: This appears to <em>always</em> be
	 * {@link eLeapConnectionStatus#NotConnected}.
	 * </p>
	 */
	public long status;

	private eLeapConnectionStatus statusE;

	public LEAP_CONNECTION_INFO()
	{
		super(ALIGN_NONE);
		size = size();
	}


	public eLeapConnectionStatus getStatus()
	{
		if (statusE == null)
		{
			statusE = eLeapConnectionStatus.parse((int) status, eLeapConnectionStatus.Unknown);
		}

		return statusE;
	}

	public static class ByReference extends LEAP_CONNECTION_INFO
			implements Structure.ByReference
	{
	}
}