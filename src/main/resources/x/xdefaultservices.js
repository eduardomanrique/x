var userService;

function init(){
	X.debug("xstartup", "XDefaultServices INIT");
	userService = X.bindService("XUserService");
	
	X.debug("xstartup", "XDefaultServices done");
}

function login(login, password, callback){
	userService.login(login, password, function(user){
		window.xuser = user;
		callback(user);
	}, null);
}

function getSessionId(callback){
	userService.getSessionId(function(sessionId){
		callback(sessionId);
	}, null);
}

function createUser(user, callback, onException){
	userService.create(user, callback, onException, null);
}

function logout(callback){
	userService.logout(callback, null);
}

_expose(init);
_external(login);
_external(createUser);
_external(logout);
_external(getSessionId);