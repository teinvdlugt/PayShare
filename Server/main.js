/*
Server code for PayShare
*/

const cluster = require('cluster');
const numCPUs = require('os').cpus().length;
const net = require('net');
const mongo = require('mongodb');
const mongoClient = mongo.MongoClient;
const googleIdTokenVerifier = require('google-id-token-verifier');
const https = require('https');
const stream = require('stream').Transform;
const crypto = require('crypto');

const PORT = 1234;//Using 1234 temporary
const HOST = "192.168.1.36";//Local IP of my computer on my home network
const VERSION = 1;
const DATABASE_URL = "mongodb://localhost:27017/payShare";//URL to mongoDB database
const GOOGLE_LOGIN_KEY = "851535968316-cqvil0i6ej1mcgs3314bqv0k460i6j4f.apps.googleusercontent.com";
const debug = process.argv.indexOf("-d")>-1;
const permisive = process.argv.indexOf("-p")>-1;
const singleThreaded = process.argv.indexOf("-s")>-1;
var db, db_users,db_sessions;

if (cluster.isMaster) {
	if(debug)console.log("Running in debug mode");
	if(permisive)console.log("Warning!! Running in permisive mode, server is vulnerable to attacks");
	if(singleThreaded)console.log("Running in signle-thread mode, performance may decrease");
	// Create as much threads as cores of the cpu to distribute load
	for (var i = 0; i < (singleThreaded?1:numCPUs); i++) {
		cluster.fork();
	}

	cluster.on('exit', (worker, code, signal) => {
		console.log(`Thread ${worker.process.pid} died`);
	});
} else {
	// Connect with database
	mongoClient.connect(DATABASE_URL, function(error, db) {
		if(error == null){
			if(debug)console.log("Connected to database");
			this.db = db;
			db_users = db.collection('users');
			db_sessions = db.collection('sessions');
			server.listen(PORT, HOST);// Make server listen when db is ready
		}else{
			console.log("Couldn't connect to database");
		}
	});

	// Create TCP server on each thread
	const server = net.createServer(function(socket) {
		
		if(debug)console.log("New connection from "+socket.remoteAddress);
		
		sessionSub = null;
		
		socket.on('data', function(data) {
			try{
				//if(data.length<2){socket.endError();return;};
				command = data.readUInt8(0);
				answer = null;
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
					answer[1] = data[1];
					data.copy(answer,2,4,size+4);
					break;
				case 2:
					size = data.readUInt16BE(2);
					if(size > data.length-4) throw new Error("Size was bigger than available");
					token = data.toString('utf8', 4, size+4);
					googleIdTokenVerifier.verify(token, GOOGLE_LOGIN_KEY, function (error, tokenInfo) {
						try{
							if (!error) {
								if(debug)console.log("User info: "+JSON.stringify(tokenInfo));
								db_users.updateOne({sub:tokenInfo.sub},{$set:{locale:tokenInfo.locale,sub:tokenInfo.sub,given_name:tokenInfo.given_name,email:tokenInfo.email}},{upsert:true},function(error,results){
									if(error != null)throw new Error("Couldn't put into db: "+error);
									db_sessions.insert({sub:tokenInfo.sub,key:crypto.randomBytes(16).toString('hex')},function(error,inserted){
										if(error != null)throw new Error("Couldn't put into db: "+error);
										console.log("New session: "+inserted.ops[0]._id+" - "+inserted.ops[0].key);
										answer = Buffer.allocUnsafe(32+24+2);
										answer[0] = 2;
										answer[1] = data[1];
										answer.write(inserted.ops[0]._id.toString(),2,inserted.ops[0]._id.toString().length);
										answer.write(inserted.ops[0].key,26,inserted.ops[0].key.length);
										socket.write(answer);
										sessionSub = inserted.ops[0].sub;
									});
									//if(debug)console.log(results);
								});
								//Download profile image and store into db
								https.request(tokenInfo.picture, function(response) {                                        
									var data = new stream();                                                    

									response.on('data', function(chunk) {                                       
										data.push(chunk);                                                         
									});                                                                         

									response.on('end', function() {  
										db_users.updateOne({sub:tokenInfo.sub},{$set:{img:mongo.Binary(data.read())}},function(error,results){
											if(error != null)throw new Error("Couldn't put into db: "+error);
											//if(debug)console.log(results);
										});	
									});                                                                         
								}).end();
							}
						}catch(error){
							handleError(error);
						}
					});
					if(debug)console.log("Login token: "+token);
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
				handleError(error);
			}
		});
		
		socket.on('close', function(data) {
			if(debug)console.log("Client disconnected");
		});
		
		socket.on('error',function(error){
			if(debug)console.log("Connection error");
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
		
		function handleError(error){
			if(debug)console.error("Error catched",error);
			socket.endError();
		}
		
	});
}