using UnityEngine;
using UnityEngine.UI;
using System.Collections.Generic;
using System;

namespace Assets.SimpleAndroidNotifications
{
	public class ScannerTestScript : MonoBehaviour
	{
		public GameObject ScannedItemPrefab;

		private float _timeout;
		private float _startScanTimeout = 10f;
		private float _startScanDelay = 0.5f;
		private bool _startScan = true;
		private Dictionary<string, ScannedItemScript> _scannedItems;

		private string storedName;

		public void OnButton()
		{
			BluetoothLEHardwareInterface.DeInitialize(() => Debug.Log("Deinitialized"));
		}

		public void OnStopScanning()
		{
			BluetoothLEHardwareInterface.Log("**************** stopping");
			BluetoothLEHardwareInterface.StopScan();
		}

		public void ScheduleSimple()
		{
			NotificationManager.Send(TimeSpan.FromSeconds(5), "Simple notification", "Customize icon and color", new Color(1, 0.3f, 0.15f));
		}

		public void ScheduleNormal()
		{
			NotificationManager.SendWithAppIcon(TimeSpan.FromSeconds(5), "Notification", "Notification with app icon", new Color(0, 0.6f, 1), NotificationIcon.Message);
		}

		public void ScheduleCustom(string name)
		{
			var notificationParams = new NotificationParams
			{
				Id = UnityEngine.Random.Range(0, int.MaxValue),
				Delay = TimeSpan.FromSeconds(1),
				Title = "Notification Alert",
				Message = name + " - user is coming near you",
				Ticker = "Ticker",
				Sound = true,
				Vibrate = true,
				Light = true,
				SmallIcon = NotificationIcon.Heart,
				SmallIconColor = new Color(0, 0.5f, 0),
				LargeIcon = "app_icon"
			};

			NotificationManager.SendCustom(notificationParams);
		}

		public void CancelAll()
		{
			NotificationManager.CancelAll();
		}

		// Use this for initialization
		void Start()
		{
			BluetoothLEHardwareInterface.Log("Start");
			_scannedItems = new Dictionary<string, ScannedItemScript>();

			BluetoothLEHardwareInterface.Initialize(true, false, () =>
			{

				_timeout = _startScanDelay;
			},
			(error) =>
			{

				BluetoothLEHardwareInterface.Log("Error: " + error);

				if (error.Contains("Bluetooth LE Not Enabled"))
					BluetoothLEHardwareInterface.BluetoothEnable(true);
			});
		}

		// Update is called once per frame
		void Update()
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

						BluetoothLEHardwareInterface.ScanForPeripheralsWithServices(null, null, (address, name, rssi, bytes) =>
						{

							BluetoothLEHardwareInterface.Log("item scanned: " + address);
							if (_scannedItems.ContainsKey(address))
							{
								var scannedItem = _scannedItems[address];
								scannedItem.TextRSSIValue.text = rssi.ToString();
								BluetoothLEHardwareInterface.Log("already in list " + rssi.ToString());
							}
							else
							{
								BluetoothLEHardwareInterface.Log("item new: " + address);
								var newItem = Instantiate(ScannedItemPrefab);
								if (newItem != null)
								{
									BluetoothLEHardwareInterface.Log("item created: " + address);
									newItem.transform.parent = transform;
									newItem.transform.localScale = Vector3.one;

									var scannedItem = newItem.GetComponent<ScannedItemScript>();
									if (scannedItem != null)
									{
										BluetoothLEHardwareInterface.Log("item set: " + address);
										scannedItem.TextAddressValue.text = address;
										scannedItem.TextNameValue.text = name;
										scannedItem.TextRSSIValue.text = rssi.ToString();
										//scannedItem.TextDistanceValue.text = _scannedItems.Values.ToString();

										//byte[] capturedBytes = bytes;
										//foreach (byte b in capturedBytes)
										//{
										//	scannedItem.TextDistanceValue.text = b.ToString();
										//}

										if (rssi < -50 && rssi > -120)
										{
											storedName = name.ToString();
											ScheduleCustom(storedName);


										}

										string b = "";
										int len = bytes.Length;
										for (int i = 0; i < len; i++)
										{
											if (i != 0)
											{
												b += ",";
											}
											b += bytes[i].ToString();
											scannedItem.TextDistanceValue.text = b;
										}


										_scannedItems[address] = scannedItem;
									}
								}
							}
						}, true);
					}
					else
					{
						BluetoothLEHardwareInterface.StopScan();
						_startScan = true;
						_timeout = _startScanDelay;
					}
				}
			}
		}
	}
}
