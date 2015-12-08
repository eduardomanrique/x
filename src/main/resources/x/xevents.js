
var execWhenReady = [];
var calledReady = false;
function _addExecuteWhenReady(fn){
	execWhenReady.push(fn);
}
var execOnEnd = [];
function _addExecuteOnEnd(fn){
	execOnEnd.push(fn);
}

function ready() {
	if(calledReady){
		return;
	}
	X.debug("xstartup", "READY function");
	calledReady = true;
	X._loaded = true;
	xdefaultservices.init();
	var isRemote = %is_remote%;
	if(!isRemote){
		X.debug("xstartup", "COMPONENTS Init..");
		xcomponents.init();
		X.debug("xstartup", "Default Services Init..");
		xutil.each(execWhenReady, function(item){
			X.debug("xstartup", "Exec execWhenReady " + item);
			item();
		});
		X.debug("xstartup", "XObj Init..");
		xobj.init();
		setTimeout(function(){
			xutil.each(execOnEnd, function(item){
				X.debug("xstartup", "Exec execOnEnd " + item);
				item();
			});
		},100);
	}
	X.debug("xstartup", "READY done");
}

function onUnloadCall(){
	var onExit;
	try{
		onExit = xobj.evalOnMainController('onExit');	
	}catch(e){}
	if(onExit){
		return onExit();
	}
}

function onStart(){
	X.debug("xstartup", "onStart");
	if ( document.addEventListener ) {
		document.addEventListener( "DOMContentLoaded", function(){
			document.removeEventListener( "DOMContentLoaded", arguments.callee, false );
			ready();
		}, false );
	} else if ( document.attachEvent ) {
		document.attachEvent("onreadystatechange", function(){
			if ( document.readyState === "complete" ) {
				document.detachEvent( "onreadystatechange", arguments.callee );
				ready();
			}
		});
		if ( document.documentElement.doScroll && window == window.top ) (function(){
			try {
				document.documentElement.doScroll("left");
			} catch( error ) {
				setTimeout( arguments.callee, 0 );
				return;
			}
			ready();
		})();
	}
	if ( window.addEventListener ) {
		window.addEventListener( "load", function(){
			ready();
		}, false );
	} else if ( window.attachEvent ) {
		window.attachEvent("onload", function(){
			ready();
		});
	}
	window.onbeforeunload = onUnloadCall;
	X.debug("xstartup", "onStart done");
}
	
_external(onStart);
_external(_addExecuteWhenReady);
_external(_addExecuteOnEnd);