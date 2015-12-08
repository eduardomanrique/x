var _debug_flags = [%debug_flags%];

function log(msg){
	console.log(msg);
}

function debug(matter, msg){
	if(_debug_flags.indexOf(matter) >=0 || (_debug_flags.length == 1  && _debug_flags[0] == 'all')){
		console.log("DEBUG (" + matter + "):" + msg);			
	}
}

function error(msg, e){
	console.log("ERROR: " + msg);
	if(e && e.stack){
		console.log("STACK_TRACE: ");
		console.log(e.stack);
	}
}

_external(log);
_external(debug);
_external(error);
