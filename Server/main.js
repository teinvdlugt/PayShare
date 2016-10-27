/*
Server code for PayShare
*/
try{
	var cluster = require('cluster');
	var os = require('os');
	var numCPUs = os.cpus().length;
	var net = require('net');
	var stdin = process.openStdin();
	var mongo = require('mongodb');
	var mongoClient = mongo.MongoClient;
	var googleIdTokenVerifier = require('google-id-token-verifier');
	var https = require('https');
	var stream = require('stream').Transform;
	var crypto = require('crypto');
}catch(error){
	console.log("Couldn't load modules, type 'npm install' to install all dependencies");
	exitError();
}

const PORT = 1234;//Using 1234 temporary
//const HOST = os.networkInterfaces()["Wi-Fi"][1].address;//Local IP of my computer on my home network, may change depending on OS and wired/wifi connection
const VERSION = 1;
const DATABASE_URL = "mongodb://localhost:27017/payShare";//URL to mongoDB database
const GOOGLE_LOGIN_KEY = "851535968316-cqvil0i6ej1mcgs3314bqv0k460i6j4f.apps.googleusercontent.com";
const debug = process.argv.indexOf("-d")>-1;
const permisive = process.argv.indexOf("-p")>-1;
const singleThreaded = process.argv.indexOf("-s")>-1;
const completeDebug = process.argv.indexOf("-c")>-1;
const help = process.argv.indexOf("-h")>-1;
var db, db_users,db_sessions,db_lists;

function exitError(){
	console.log("Server load stopped");
	process.exit();
}

if(help){
	console.log("PayShare server help message:\n -d Show debug messages\n -p Run in permissive mode\n -s Run in single-threaded mode\n -c Run in complete debug mode\n -h Show this message");
	process.exit();
}

if (cluster.isMaster) {
	console.log("Available network interfaces to run on:");
	inter = os.networkInterfaces();
	selectable = [];
	for(index in inter) {
		console.log("\t"+index);
		for(vez=0;vez<inter[index].length;vez++){
			console.log("\t\t"+selectable.length+". "+inter[index][vez].address);
			selectable.push(inter[index][vez].address);
		}
	}
	process.stdout.write("Enter number of IP to run server on from the list above:");

	stdin.on("data", function(d) {
		stdin.on("data",function(){console.log("Cannot enter any command");});
		var HOST = selectable[d.toString().trim()];
		if(HOST == null){
			console.log(d.toString().trim()+" is not a valid option");
			exitError();
		}
		if(debug)console.log("Running in debug mode");
		if(permisive)console.log("Warning!! Running in permisive mode, server is vulnerable to attacks");
		if(singleThreaded)console.log("Running in signle-thread mode, performance may decrease");
		if(completeDebug)console.log("Running in complete debug mode");
		console.log("Loading server at IP "+HOST+"...");
		if(!debug){process.stdout.write("<");animation = setInterval(function(){process.stdout.write("-");},20)};
		
		var ready = 0;
		for (var i = 0; i < (singleThreaded?1:numCPUs); i++) {// Create as much threads as cores of the cpu to distribute load
			child = cluster.fork({"HOST":HOST});
			child.on('message',function(message){
				if(message == "ready"){
					ready++;
					if(ready == numCPUs){
						if(!debug){clearInterval(animation);console.log(">");}
						console.log("All ready! =)");
					}
				}else if(message == "error_db"){
					clearInterval(animation);console.log(">");
					console.log("Couldn't connect to database, check mongodb is running on localhost and default port");
					exitError();
				}
			});
		}

		cluster.on('exit', (worker, code, signal) => {
			console.log(`Thread ${worker.process.pid} died`);
		});
	});
} else {
	var HOST = process.env.HOST;
	// Connect with database
	mongoClient.connect(DATABASE_URL, function(error, db) {
		if(error == null){
			if(debug)console.log("Connected to database");
			this.db = db;
			db_users = db.collection('users');
			db_sessions = db.collection('sessions');
			db_lists = db.collection('lists');
			server.listen(PORT, HOST);// Make server listen when db is ready
		}else{
			if(debug){
				console.log("Couldn't connect to database");
			}
			else{
				process.send("error_db");
			}
		}
	});

	// Create TCP server on each thread
	const server = net.createServer(function(socket) {
		
		if(debug)console.log("New connection from "+socket.remoteAddress);
		
		//sessionSub = null;
		sessionUserId = null;
		
		function sendInfo(id){
			//db_users.find({sub:sessionSub}).toArray(function(error,result){
			db_users.find({_id:sessionUserId}).toArray(function(error,result){
				try{
					if(error != null)throw new Error("Couldn't get from db: "+error);
					if(result.length<1)throw new Error("No users found");
					result = result[0];
					nameLength = Buffer.byteLength(result.name);
					emailLength = Buffer.byteLength(result.email);
					answer = Buffer.alloc(4+24+nameLength+emailLength);
					answer[0] = 5;
					answer[1] = id;
					answer.write(result._id.toString(),2,24);
					answer[26] = nameLength;
					offset = answer[26]+27;
					answer.write(result.name,27,offset);
					answer[offset] = emailLength;
					answer.write(result.email,offset+1,offset+1+answer[offset]);
					send(answer);
				}catch(error){handleError(error);}
				//console.log("Info: "+JSON.stringify(answer));
			});
		}
		
		function send(answer){
			if(debug)console.log("Sent "+answer.length+" bytes"+(completeDebug?": "+JSON.stringify(answer):""));
			socket.write(answer);
		}
		
		function processData(data){
			try{
				if(debug)
				console.log("Proccess "+data.length+" bytes"+(completeDebug?": "+JSON.stringify(data):""));
				//if(data.length<2){socket.endError();return;};
				command = data.readUInt8(0);
				answer = null;
				switch(command){
				case 0://Info request
					limit = 2;
					//if(data.length > limit){processData(data.slice(limit));}
					answer = Buffer.from([0,data.readUInt8(1),VERSION]);//Answer requested command, request ID and VERSION
					break;
				case 1://Echo (test) request
					//if(data.length<4){socket.endError();return;};
					size = data.readUInt16BE(2);
					if(size > data.length-4) throw new Error("Size was bigger than available");
					limit = 4+size;
					//if(data.length > limit){processData(data.slice(limit));}
					answer = Buffer.allocUnsafe(size+2);
					answer[0] = 1;
					answer[1] = data[1];
					data.copy(answer,2,4,size+4);
					break;
				case 2:
					socket.end();
					socket.destroy();
					break;
				case 3:
					size = data.readUInt16BE(2);
					if(size > data.length-4) throw new Error("Size was bigger than available");
					limit = 4+size;
					//if(data.length > limit){processData(data.slice(limit));}
					token = data.toString('utf8', 4, size+4);
					googleIdTokenVerifier.verify(token, GOOGLE_LOGIN_KEY, function (error, tokenInfo) {
						try{
							if (!error) {
								if(debug)console.log("User info: "+JSON.stringify(tokenInfo));
								db_users.update({sub:tokenInfo.sub},{$set:{locale:tokenInfo.locale,sub:tokenInfo.sub,name:tokenInfo.given_name,email:tokenInfo.email,lists:[]}},{upsert:true},function(error,results){
									try{
										if(error != null)throw new Error("Couldn't put into db: "+error);
										db_users.find({sub:tokenInfo.sub}).toArray(function(error,results){
											try{
												db_sessions.insert({uID:results[0]._id,key:crypto.randomBytes(16).toString('hex')},function(error,inserted){
													try{
														if(error != null)throw new Error("Couldn't put into db: "+error);
														if(debug)console.log("New session: "+inserted.ops[0]._id+" - "+inserted.ops[0].key);
														answer = Buffer.allocUnsafe(32+24+2);
														answer[0] = 3;
														answer[1] = data[1];
														answer.write(inserted.ops[0]._id.toString(),2,inserted.ops[0]._id.toString().length);
														answer.write(inserted.ops[0].key,26,inserted.ops[0].key.length);
														send(answer);
														sessionUserId = inserted.ops[0].uID
														//sessionSub = inserted.ops[0].sub;
														sendInfo(0);
													}catch(error){handleError(error);}
												});
											}catch(error){handleError(error);}
										});
									}catch(error){handleError(error);}
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
				case 4:
					//if(data.length!=32+24+2) throw new Error("Size not match");
					limit = 32+24+2;
					//if(data.length > limit){processData(data.slice(limit));}
					id = data.toString('utf8',2,26);
					key = data.toString('utf8',26,58);
					db_sessions.find({_id:mongo.ObjectId(id),key:key}).toArray(function(error,result){
						try{
							if(error != null)throw new Error("Couldn't get from db: "+error);
							if(result.length < 1){
								answer = Buffer.from([4,data[1],0]);
								send(answer);
								if(debug)console.log("Login error");
							}else{
								//sessionSub = result[0].sub;
								sessionUserId = result[0].uID;
								if(debug)console.log("Session uID: "+sessionUserId);
								answer = Buffer.from([4,data[1],1]);
								send(answer);
								sendInfo(data[1]);
							}
						}catch(error){handleError(error);}
					});
					break;
				case 5:
					limit = 2;
					//if(data.length > limit){processData(data.slice(limit));}
					sendInfo(data[1]);
					break;
				case 6:
					//if(data.length!=24+2) throw new Error("Size not match ("+data.length+")");
					if(debug)console.log("Got image request");
					limit = 24+2;
					//if(data.length > limit){processData(data.slice(limit));}
					id = data.toString('utf8',2,26);
					db_users.find({_id:mongo.ObjectId(id)}).toArray(function(error,result){
						try{
							if(error != null)throw new Error("Couldn't get from db: "+error);
							img = result[0].img;
							if(img != null){
								answer = Buffer.allocUnsafe(24+6+img.length());
								answer.write(id,2,24);
								answer.writeUInt32BE(img.length(),26);
								img.read(0,img.length()).copy(answer,30);
							}else{
								answer = Buffer.allocUnsafe(24+6);
								answer.write(id,2,24);
								answer.writeUInt32BE(0,26);
							}
							answer[0] = 6;
							answer[1] = data[1];
							send(answer);
							//console.log("Sent: "+JSON.stringify(answer));
						}catch(error){handleError(error);}
					});
					break;
				case 7:
					limit = 2;
					//if(data.length > limit){processData(data.slice(limit));}
					if(sessionUserId == null)throw new Error("User not logged trying to get info");
					db_users.find({_id:sessionUserId}).toArray(function(error,result){
						try{
							if(error != null)throw new Error("Couldn't get from db: "+error);
							ids = [];
							for(vez=0;vez<result[0].lists.length;vez++){
								ids[vez] = result[0].lists[vez].id;
							}
							//ids = [];
							db_lists.find({_id:{$in:ids}}).toArray(function(error,result){
								try{
									if(error != null)throw new Error("Couldn't get from db: "+error);
									//result = result[0];
									size = 3;
									for(vez=0;vez<result.length;vez++){
										size+= 25;
										size+= Buffer.byteLength(result[vez].name);
									}
									
									answer = Buffer.alloc(size);
									answer[0] = 7;
									answer[1] = data[1];
									answer[2] = result.length;
									offset = 3;
									for(vez=0;vez<result.length;vez++){
										nameSize = Buffer.byteLength(result[vez].name);
										answer.write(result[vez]._id.toString(),offset,result[vez]._id.toString().length);
										answer[offset+24] = nameSize;
										answer.write(result[vez].name,offset+25,nameSize);
										offset += nameSize+25;
									}
									send(answer);
								}catch(error){handleError(error);}
							});
							
						}catch(error){handleError(error);}
					});
					break;
				case 8:
					size = data.readUInt16BE(2);
					if(size > data.length-4) throw new Error("Size was bigger than available");
					limit = 4+size;
					if(sessionUserId == null)throw new Error("User not logged trying to get info");
					//if(data.length > limit){processData(data.slice(limit));}
					name = data.toString('utf8', 4, size+4);
					if(name.length < 100){
						db_lists.insert({name:name,public:false,access_by:[sessionUserId],currency:"EUR",items:[{name:"Potatoes",by:sessionUserId,price:5.12,amount:5,_id:new mongo.ObjectId()}]},function(error,result){
							if(error != null)throw new Error("Couldn't get from db: "+error);
							//console.log("New item: "+JSON.stringify(data));
							db_users.updateOne({_id:sessionUserId},{$push:{lists:{id:result.ops[0]._id,type:1}}},{upsert:false},function(error,results){
								if(debug)console.log("New list inserted");
								processData(Buffer.from([7,data[1]]));
							});
						});
					}else{
						throw new Error("List name was bigger than expected");
					}
					break;
				case 9:
					limit = 2+24;
					if(sessionUserId == null)throw new Error("User not logged trying to get info");
					id = data.toString('utf8',2,26);
					_id:mongo.ObjectId(id);
					
					db_lists.find({_id:mongo.ObjectId(id)}).toArray(function(error,result){
						try{
							if(error != null)throw new Error("Couldn't get from db: "+error);
							accessible = false;
							result = result[0];
							if(!(result.public || result.access_by.indexOf(sessionUserId) > -1)){
								nameSize = Buffer.byteLength(result.name);
								size = 9+nameSize;
								for(vez=0;vez<result.items.length;vez++){
									size+= 48+4+2+4;
									size+= Buffer.byteLength(result.items[vez].name);
								}
								
								answer = Buffer.alloc(size);
								answer[0] = 9;
								answer[1] = data[1];
								answer[2] = 1;
								answer[3] = nameSize;
								answer.write(result.name,4,nameSize);
								answer[4+nameSize] = result.public;
								answer.write(result.currency,5+nameSize,3);
								answer[8+nameSize] = result.items.length;
								offset = 9+nameSize;
								for(vez=0;vez<result.items.length;vez++){
									nameSize = Buffer.byteLength(result.items[vez].name);
									answer.write(result.items[vez].by.toString(),offset,24);
									answer.write(result.items[vez]._id.toString(),offset+24,24);
									answer[offset+48] = nameSize;
									answer.write(result.items[vez].name,offset+49,nameSize);
									offset += nameSize+49;
									answer.writeFloatBE(result.items[vez].price,offset);
									answer[offset+4] = result.items[vez].amount;
									answer.writeUInt32BE(result.items[vez]._id.getTimestamp().getTime()/1000,offset+5);
									offset += 9;
								}
							}else{
								if(debug)console.log("No list found");
								answer = Buffer.alloc(size);
								answer[0] = 9;
								answer[1] = data[1];
								answer[2] = 0;
							}
							send(answer);
						}catch(error){handleError(error);}
					});
					break;
				}
				
				if(answer != null){send(answer);}
				if(data.length > limit){processData(data.slice(limit));}//There are more request on the data received so process them
				
			}catch(error){
				handleError(error);
			}
		}
		
		socket.on('data', function(data) {
			if(debug){
				console.log("Received "+data.length+" bytes"+(completeDebug?": "+JSON.stringify(data):""));
				/*message = "Data: ";
					for(a=0;a < data.length;a++){
						message += data.readUInt8(a)+", ";
					}
					console.log(message);
					console.log(answer);*/
			}
			processData(data);
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
		
	}).on('listening',function(){process.send("ready");/*Tell main thread we are ready/*console.log("Ready - "+cluster.worker.id);*/});
}