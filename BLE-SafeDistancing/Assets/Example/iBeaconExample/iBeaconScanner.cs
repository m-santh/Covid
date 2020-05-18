using UnityEngine;
using UnityEngine.UI;
using System.Collections.Generic;
using System;
using System.Linq;

public class iBeaconScanner : MonoBehaviour
{
	public GameObject iBeaconItemPrefab;

	private float _timeout = 0f;
	private float _startScanTimeout = 10f;
	private float _startScanDelay = 0.5f;
	private bool _startScan = true;
	private Dictionary<string, iBeaconItemScript> _iBeaconItems;

	//private class BeaconData
	//{
	//	public Guid Uuid { get; set; }
	//	public ushort Major { get; set; }
	//	public ushort Minor { get; set; }
	//	public sbyte TxPower { get; set; }
	//	public static BeaconData FromBytes(byte[] bytes)
	//	{
	//		if (bytes[0] != 0x02) { throw new ArgumentException("First byte in array was exptected to be 0x02", "bytes"); }
	//		if (bytes[1] != 0x15) { throw new ArgumentException("Second byte in array was expected to be 0x15", "bytes"); }
	//		if (bytes.Length != 23) { throw new ArgumentException("Byte array length was expected to be 23", "bytes"); }
	//		return new BeaconData
	//		{
	//			Uuid = new Guid(
	//					BitConverter.ToInt32(bytes.Skip(2).Take(4).Reverse().ToArray(), 0),
	//					BitConverter.ToInt16(bytes.Skip(6).Take(2).Reverse().ToArray(), 0),
	//					BitConverter.ToInt16(bytes.Skip(8).Take(2).Reverse().ToArray(), 0),
	//					bytes.Skip(10).Take(8).ToArray()),
	//			Major = BitConverter.ToUInt16(bytes.Skip(18).Take(2).Reverse().ToArray(), 0),
	//			Minor = BitConverter.ToUInt16(bytes.Skip(20).Take(2).Reverse().ToArray(), 0),
	//			TxPower = (sbyte)bytes[22]
	//		};
	//	}
	//	public static BeaconData FromBuffer(IBuffer buffer)
	//	{
	//		var bytes = new byte[buffer.Length];
	//		using (var reader = DataReader.FromBuffer(buffer))
	//		{
	//			reader.ReadBytes(bytes);
	//		}
	//		return BeaconData.FromBytes(bytes);
	//	}
	//}

	//static void Main(string[] args)
	//{
	//	var watcher = new BluetoothLEAdvertisementWatcher();
	//	watcher.Received += Watcher_Received;
	//	watcher.Start();
	//	Console.WriteLine("Bluetooth LE Advertisement Watcher Started (Press ESC to exit)");
	//	while (Console.ReadKey().Key != ConsoleKey.Escape)
	//	{
	//	}
	//	watcher.Stop();
	//	Console.WriteLine("Bluetooth LE Advertisement Watcher Stopped");
	//}

	//private static void Watcher_Received(BluetoothLEAdvertisementWatcher sender, BluetoothLEAdvertisementReceivedEventArgs args)
	//{
	//	const ushort AppleCompanyId = 0x004C;
	//	foreach (var adv in args.Advertisement.ManufacturerData.Where(x => x.CompanyId == AppleCompanyId))
	//	{
	//		var beaconData = BeaconData.FromBuffer(adv.Data);
	//		Console.WriteLine(
	//			"[{0}] {1}:{2}:{3} TxPower={4}, Rssi={5}",
	//			args.Timestamp,
	//			beaconData.Uuid,
	//			beaconData.Major,
	//			beaconData.Minor,
	//			beaconData.TxPower,
	//			args.RawSignalStrengthInDBm);
	//	}
	//}



	// Use this for initialization
	void Start ()
	{
		_iBeaconItems = new Dictionary<string, iBeaconItemScript> ();

		BluetoothLEHardwareInterface.Initialize (true, false, () => {

			_timeout = _startScanDelay;
			
			BluetoothLEHardwareInterface.BluetoothScanMode (BluetoothLEHardwareInterface.ScanMode.LowLatency);
			BluetoothLEHardwareInterface.BluetoothConnectionPriority (BluetoothLEHardwareInterface.ConnectionPriority.High);
		}, 
		(error) => {
			
			BluetoothLEHardwareInterface.Log ("Error: " + error);

			if (error.Contains ("Bluetooth LE Not Enabled"))
				BluetoothLEHardwareInterface.BluetoothEnable (true);
		});
	}

	public float Distance (float signalPower, float rssi, float nValue)
	{
		return (float)Math.Pow (10, ((signalPower - rssi) / (10 * nValue)));
	}

	// Update is called once per frame
	void Update ()
	{
		if (_timeout > 0f)
		{
			_timeout -= Time.deltaTime;
			if (_timeout <= 0f)
			{
				if (_startScan)
				{
					_startScan = false;
					_timeout = _startScanTimeout;

					// scanning for iBeacon devices requires that you know the Proximity UUID and provide an Identifier
					
					BluetoothLEHardwareInterface.ScanForBeacons(new string[] { "9bda0dbb-1fae-436a-bfb0-0c2731d0ee3b:oneplus6" }, (iBeaconData) => {
					//BluetoothLEHardwareInterface.ScanForBeacons(new string[] { "01020304-0506-0708-0910-111213141516:Pit01" }, (iBeaconData) => {

						if (!_iBeaconItems.ContainsKey (iBeaconData.UUID))
						{
							BluetoothLEHardwareInterface.Log ("item new: " + iBeaconData.UUID);
							var newItem = Instantiate (iBeaconItemPrefab);
							if (newItem != null)
							{
								BluetoothLEHardwareInterface.Log ("item created: " + iBeaconData.UUID);
								newItem.transform.SetParent (transform);
								newItem.transform.localScale = new Vector3 (1f, 1f, 1f);

								var iBeaconItem = newItem.GetComponent<iBeaconItemScript> ();
								if (iBeaconItem != null)
									_iBeaconItems[iBeaconData.UUID] = iBeaconItem;
							}
						}

						if (_iBeaconItems.ContainsKey (iBeaconData.UUID))
						{
							var iBeaconItem = _iBeaconItems[iBeaconData.UUID];
							iBeaconItem.TextUUID.text = iBeaconData.UUID;
							iBeaconItem.TextRSSIValue.text = iBeaconData.RSSI.ToString ();

							// Android returns the signal power or measured power, iOS hides this and there is no way to get it
							iBeaconItem.TextAndroidSignalPower.text = iBeaconData.AndroidSignalPower.ToString ();

							// iOS returns an enum of unknown, far, near, immediate, Android does not return this
							iBeaconItem.TextiOSProximity.text = iBeaconData.iOSProximity.ToString ();

							// we can only calculate a distance if we have the signal power which iOS does not provide
							if (iBeaconData.AndroidSignalPower != 0)
								iBeaconItem.TextDistance.text = Distance (iBeaconData.AndroidSignalPower, iBeaconData.RSSI, 2.5f).ToString ();
						}
					});
				}
				else
				{
					BluetoothLEHardwareInterface.StopScan ();
					_startScan = true;
					_timeout = _startScanDelay;
				}
			}
		}
	}
}
