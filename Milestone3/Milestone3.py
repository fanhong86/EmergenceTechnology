import aioblescan as aiobs
from aioblescan.plugins import EddyStone
import asyncio
import paho.mqtt.client as mqtt
import time

def _process_packet(data):
    ev = aiobs.HCI_Event()
    xx = ev.decode(data)
    xx = EddyStone().decode(ev)
    if xx:
        if "team8" in xx['url']:
            value = format(xx['url'][13:21])
            print(value)
            print("sending pulication")
            client.publish(publishtopic, value)

if __name__ == '__main__':
    
    broker_address = "192.168.4.1" #enter your broker address here
    subscribetopic = "testTopic1"
    publishtopic = "testTopic2"
    client = mqtt.Client("P1")
    client.connect(broker_address)
    mydev = 0
    
    event_loop = asyncio.get_event_loop()
    mysocket = aiobs.create_bt_socket(mydev)
    fac = event_loop._create_connection_transport(mysocket,aiobs.BLEScanRequester,None,None)
    conn, btctrl = event_loop.run_until_complete(fac)
    
    client.loop_start()
    btctrl.process = _process_packet
    btctrl.send_scan_request()
    client.subscribe(subscribetopic)
    time.sleep(10)
    client.loop_stop()
    
    try:
        event_loop.run_forever()
    except KeyboardInterrupt:
        print('keyboard interrupt')
    finally:
        print('closing event loop')
        btctrl.stop_scan_request()
        conn.close()
        event_loop.close()