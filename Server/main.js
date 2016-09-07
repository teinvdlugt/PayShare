/*
Server code for PayShare
*/

const cluster = require('cluster');
const numCPUs = require('os').cpus().length;
const net = require('net');

const PORT = 1234;//Using 1234 temporary
const VERSION = 1;
const debug = process.argv.indexOf("-d")>-1;
const permisive = process.argv.indexOf("-p")>-1;

if (cluster.isMaster) {
	if(debug)console.log("Running in debug mode");
	if(permisive)console.log("Warning!! Running in permisive mode, server is vulnerable to attacks");
	// Create as much threads as cores of the cpu to distribute load
	for (var i = 0; i < numCPUs; i++) {
		cluster.fork();
	}

	cluster.on('exit', (worker, code, signal) => {
		console.log(`Thread ${worker.process.pid} died`);
	});
} else {
	// Create TCP server on each thread
	const server = net.createServer(function(socket) {
		
		if(debug)console.log("New connection from "+socket.remoteAddress);
		
		socket.on('data', function(data) {
			try{
			//if(data.length<2){socket.endError();return;};
			command = data.readUInt8(0);
			switch(command){
				case 0://Info request
				answer = Buffer.from([0,data.readUInt8(1),VERSION]);//Answer requested command, request ID and VERSION
				break;
				case 1://Echo (test) request
				//if(data.length<4){socket.endError();return;};
				size = data.readUInt16BE(2);
				if(size > data.length-4) throw new Error("Size was bigger than available");
				answer = Buffer.allocUnsafe(size+2);
				answer[0] = 1;
				answer[1] = data.readUInt8(1);
				data.copy(answer,2,4,size+4);
				break;
			}
			if(typeof answer !== 'undefined')socket.write(answer);
			
			if(debug){
				console.log("Received "+data.length+" bytes");
				message = "Data: ";
				for(a=0;a < data.length;a++){
					message += data.readUInt8(a)+", ";
				}
				console.log(message);
				console.log(answer);
			}
			}catch(error){
				if(debug)console.error("Error catched",error);
				socket.endError();
			}
		});
		
		socket.on('close', function(data) {
			if(debug)console.log("Client disconnected");
		});
		
		socket.endError = function(){
			if(!permisive){
			if(debug)console.log("Forced connection close due to illegal request")
			socket.end(Buffer.from([2,1]));
			socket.destroy();
			}else{
			if(debug)console.log("Illegal request was made and ignored due to permisive mode");
			}
		}
		
	}).listen(PORT, "localhost");
}