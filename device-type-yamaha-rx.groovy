/**
 *  Yamaha Network Receiver
 *     Works on RX-V*
 *    SmartThings driver to connect your Yamaha Network Receiver to SmartThings
 *
 *  Loosely based on: https://github.com/BirdAPI/yamaha-network-receivers
 *   and: http://openremote.org/display/forums/Controlling++RX+2065+Yamaha+Amp
 */

preferences {
	input("destIp", "text", title: "IP", description: "The device IP")
    input("destPort", "number", title: "Port", description: "The port you wish to connect")
    input("inputChan","enum", title: "Input Control", description: "Select the inputs you want to use", options: ["TUNER","HDMI1","HDMI2","HDMI3","HDMI4","HDMI5","AV1","AV2","AV3","AV4","AV5","V-AUX","AUDIO1","AUIDO2","SERVER","NET RADIO","USB"],multiple: true,required: true)
}
 

metadata {
	definition (name: "Yamaha Network Receiver", namespace: "KristopherKubicki", 
    	author: "kristopher@acm.org") {
        capability "Actuator"
		capability "Switch" 
        capability "Polling"
        capability "Music Player"
        
        attribute "input", "string"
        
        command "inputSelect"
        command "inputNext"
        
      	}

	simulator {
		// TODO-: define status and reply messages here
	}

	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: false, canChangeBackground: true) {
            state "on", label: '${name}', action:"switch.off", backgroundColor: "#79b821", icon:"st.Electronics.electronics16"
            state "off", label: '${name}', action:"switch.on", backgroundColor: "#ffffff", icon:"st.Electronics.electronics16"
        }
		standardTile("poll", "device.poll", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
			state "poll", label: "", action: "polling.poll", icon: "st.secondary.refresh", backgroundColor: "#FFFFFF"
		}
        standardTile("input", "device.input", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
			state "input", label: '${currentValue}', action: "inputNext", icon: "", backgroundColor: "#FFFFFF"
		}
        standardTile("mute", "device.mute", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
            state "muted", label: '${name}', action:"unmute", backgroundColor: "#79b821", icon:"st.Electronics.electronics13"
            state "unmuted", label: '${name}', action:"mute", backgroundColor: "#ffffff", icon:"st.Electronics.electronics13"
		}
        controlTile("level", "device.level", "slider", height: 1, width: 2, inactiveLabel: false, range: "(-800..165)") {
			state "level", label: '${name}', action:"setLevel"
		}
        
		main "switch"
        details(["switch","input","mute","level","poll"])
	}
}



def parse(String description) {
//	log.debug "Parsing '${description}'"
    
 	def map = stringToMap(description)
    if(!map.body) { return }
	def body = new String(map.body.decodeBase64())

	def statusrsp = new XmlSlurper().parseText(body)
	def power = statusrsp.Main_Zone.Basic_Status.Power_Control.Power.text()
    if(power == "On") { 
    	sendEvent(name: "switch", value: 'on')
    }
    if(power != "" && power != "On") { 
    	sendEvent(name: "switch", value: 'off')
    }
    
    def inputChan = statusrsp.Main_Zone.Basic_Status.Input.Input_Sel.text()
    if(inputChan != "") { 
    	sendEvent(name: "input", value: inputChan)
	}

    def muteLevel = statusrsp.Main_Zone.Basic_Status.Volume.Mute.text()
    if(muteLevel == "On") { 
    	sendEvent(name: "mute", value: 'muted')
	}
    if(muteLevel != "" && muteLevel != "On") {
	    sendEvent(name: "mute", value: 'unmuted')
    }
    
    
    if(statusrsp.Main_Zone.Basic_Status.Volume.Lvl.Val.text()) { 
    	def volLevel = statusrsp.Main_Zone.Basic_Status.Volume.Lvl.Val.toBigInteger()
    	if(volLevel != device.currentValue("level") as Integer) {
    		sendEvent(name: "level", value: volLevel)
        }
    }

    //log.debug "MATCH: '${volLevel}'"
}

// Needs to round to the nearest 5
def setLevel(val) {
	val = ((val/5) as Integer) * 5
	sendEvent(name: "mute", value: "unmuted")
    sendEvent(name: "level", value: val)    
    request("<YAMAHA_AV cmd=\"PUT\"><Main_Zone><Volume><Lvl><Val>$val</Val><Exp>1</Exp><Unit>dB</Unit></Lvl></Volume></Main_Zone></YAMAHA_AV>")
}

def on() {
	request('<YAMAHA_AV cmd=\"PUT\"><Main_Zone><Power_Control><Power>On</Power></Power_Control></Main_Zone></YAMAHA_AV>')
}

def off() { 
	request('<YAMAHA_AV cmd="PUT"><Main_Zone><Power_Control><Power>Standby</Power></Power_Control></Main_Zone></YAMAHA_AV>')
}

def mute() { 
	sendEvent(name: "mute", value: "muted")
	request('<YAMAHA_AV cmd="PUT"><Main_Zone><Volume><Mute>On</Mute></Volume></Main_Zone></YAMAHA_AV>')
}

def unmute() { 
	sendEvent(name: "mute", value: "unmuted")
	request('<YAMAHA_AV cmd="PUT"><Main_Zone><Volume><Mute>Off</Mute></Volume></Main_Zone></YAMAHA_AV>')
}

def inputNext() { 

	def cur = device.currentValue("input")
    def selectedInputs = ["HDMI1","HDMI2","HDMI5","AV1","HDMI1"]
    
    
    def semaphore = 0
    for(selectedInput in selectedInputs) {
    	if(semaphore == 1) { 
        	return inputSelect(selectedInput)
        }
    	if(cur == selectedInput) { 
        	semaphore = 1
        }
    }
}


def inputSelect(channel) {
 	sendEvent(name: "input", value: channel	)
	request("<YAMAHA_AV cmd=\"PUT\"><Main_Zone><Input><Input_Sel>$channel</Input_Sel></Input></Main_Zone></YAMAHA_AV>")
}

def poll() { 
	status()
}

def status() {
    request('<YAMAHA_AV cmd="GET"><Main_Zone><Basic_Status>GetParam</Basic_Status></Main_Zone></YAMAHA_AV>')
}

def request(body) { 

    def hosthex = convertIPtoHex(destIp)
    def porthex = convertPortToHex(destPort)
    device.deviceNetworkId = "$hosthex:$porthex" 

    def hubAction = new physicalgraph.device.HubAction(
   	 		'method': 'POST',
    		'path': "/YamahaRemoteControl/ctrl",
        	'body': body,
        	'headers': [ HOST: "$destIp:$destPort" ]
		) 
        
 //   log.debug hubAction    
        
    hubAction
}


private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}
