var _default_decimal_separator = ',';
var _default_thousand_separator = '.';

%currency_formatter%

function currencyFormatter(decimals, decSep, thousSep){
	return {
		format: function(v){
			var n = v, 
			    c = isNaN(decimals = Math.abs(decimals)) ? 2 : decimals, 
			    d = decSep == undefined ? _default_decimal_separator : decSep, 
			    t = thousSep == undefined ? _default_thousand_separator : thousSep, 
			    s = n < 0 ? "-" : "", 
			    i = parseInt(n = Math.abs(+n || 0).toFixed(c)) + "", 
			    j = (j = i.length) > 3 ? j % 3 : 0;
			return s + (j ? i.substr(0, j) + t : "") + i.substr(j).replace(/(\d{3})(?=\d)/g, "$1" + t) + (c ? d + Math.abs(n - i).toFixed(c).slice(2) : "");
		}
	};
}

var _default_formatter = currencyFormatter();

function getNumber(s, returnEmpty){
	var def = returnEmpty ? '' : '0';
	var r = '';
	for(var i in s){
		if('0123456789'.indexOf(s[i]) >= 0){
			r += s[i];
		}
	}
	return r == '' ? def : r;
}

function maskFloat(kc, e){
	var unformated = getNumber(e.target.value);
	if(kc == 8){
		unformated = unformated.substring(0, unformated.length -2);
	}
	if(kc >= 48 && kc <= 57){
		unformated += kc - 48;
	} else if(kc >= 96 && kc <= 105){
		unformated += kc - 96;
	}
	e.target.value = _default_formatter.format(parseInt(unformated)/100);
	return false;
}

function maskInt(kc, e){
	return (kc >= 48 && kc <= 57) || (kc >= 96 && kc <= 105) || kc == 39 || kc == 37 || kc == 8 || kc == 46;
}

function maskDate(kc, e){
	var unformated = getNumber(e.target.value, true);
	if(kc == 8){
		unformated = unformated.substring(0, unformated.length -1);
	}
	if(kc >= 48 && kc <= 57 && unformated.length < 8){
		unformated += (kc - 48);
	} else if(kc >= 96 && kc <= 105 && unformated.length < 8){
		unformated += (kc - 96);
	}
	if(unformated.length >= 4){
		unformated = unformated[0] + unformated[1] + '/' + unformated[2] + unformated[3] + '/' + unformated.substring(4);
	}else if(unformated.length >= 2){
		unformated = unformated[0] + unformated[1] + '/' + unformated.substring(2);
	}
	e.target.value = unformated;
	return false;
}

function maskDatetime(kc, e){
	var unformated = getNumber(e.target.value, true);
	if(kc == 8){
		unformated = unformated.substring(0, unformated.length -1);
	}
	if(kc >= 48 && kc <= 57 && unformated.length < 12){
		unformated += (kc - 48);
	}else if(kc >= 96 && kc <= 105 && unformated.length < 12){
		unformated += (kc - 96);
	}
	if(unformated.length >= 10){
		unformated = unformated[0] + unformated[1] + '/' + unformated[2] + unformated[3] + '/' + 
				unformated[4] + unformated[5] + unformated[6] + unformated[7] + ' ' + unformated[8] + unformated[9] + ':' + unformated.substring(10);
	}else if(unformated.length >= 8){
		unformated = unformated[0] + unformated[1] + '/' + unformated[2] + unformated[3] + '/' + 
				unformated[4] + unformated[5] + unformated[6] + unformated[7] + ' ' + unformated.substring(8);
	}else if(unformated.length >= 4){
		unformated = unformated[0] + unformated[1] + '/' + unformated[2] + unformated[3] + '/' + unformated.substring(4);
	}else if(unformated.length >= 2){
		unformated = unformated[0] + unformated[1] + '/' + unformated.substring(2);
	}
	e.target.value = unformated;
	return false;
}

function maskIFloat(kc, e){
	return e.target.value.split[','][1].lengt <= 2 && ((kc >= 48 && kc <= 57) || (kc >= 96 && kc <= 105) || kc == 39 || kc == 37 || kc == 8 || kc == 46
		|| ((kc == 188 || kc == 108) && e.target.value.indexOf(',') < 0));
}

function mask(e){
	if (!e) e = window.event;
	if(e.type == 'keydown'){
		var kc = e.keyCode;
		if(kc != 9){
			var xtype = e.target.getAttribute("xtype");
			if(xtype == 'ifloat'){
				return maskIFloat(kc, e);
			}else if(xtype == 'float'){
				return maskFloat(kc, e);
			}else if(xtype == 'int' || xtype == 'percent'){
				return maskInt(kc, e);
			}else if(xtype == 'date'){
				return maskDate(kc, e);
			}else if(xtype == 'datetime'){
				return maskDatetime(kc, e);
			}
		}
	}
}

function getDefaultFormatter(){
	return _default_formatter;
}


function parseDate(strDt, format){
	if(!strDt){
		return '';
	}
	var dt = new Date(strDt);
	if(!isNaN(dt.getYear())){
		return dt;
	}
	var formatSp = format.split('/');
	var dtSplit = strDt.split("/");
	dt = new Date();
	dt.setDate(dtSplit[formatSp.indexOf('d')]);
	dt.setMonth(dtSplit[formatSp.indexOf('m')]-1);
	dt.setYear(dtSplit[formatSp.indexOf('y')]);
	dt.setHours(0);
	dt.setMinutes(0);
	dt.setSeconds(0);
	dt.setMilliseconds(0);
	return dt;
}

function padR(str, c, len){
	if(!str){
		str = "";
	}else{
		str = str + "";
	}
	while(str.length < len){
		str += c;
	}
	return str;
}

function padL(str, c, len){
	if(!str){
		str = "";
	}else{
		str = str + "";
	}
	while(str.length < len){
		str = c + str;
	}
	return str;
}

function applyDateMask(s, format){
	var fs = format.split('/');
	var index = 0;
	var result = []
	for(var i = 0; i < fs.length; i++){
		if(fs[i] == 'd'){
			result.push(s[index++] + s[index++]);
		}else if(fs[i] == 'm'){
			result.push(s[index++] + s[index++]);
		}else if(fs[i] == 'y'){
			result.push(s[index++] + s[index++] + s[index++] + s[index++]);
		} 
	}
	return result.join('/');
}

function formatDate(d, format){
	if(!d){
		return "";
	}
	if(typeof(d) == 'string'){
		d = new Date(d);
	}
	var formatSp = format.split('/');
	var a = [padL(d.getDate(), '0', 2), padL(d.getMonth() + 1, '0', 2), padL(d.getFullYear(), '0', 4)];
	var result = [];
	for(var i = 0; i < formatSp.length; i++){
		result.push(a[formatSp.indexOf(formatSp[i])]);
	}
	return result.join('/');
}

_external(currencyFormatter);
_external(mask);
_expose(getNumber);
_expose(getDefaultFormatter);
_external(formatDate);
_external(parseDate);
_external(applyDateMask);
_external(padL);
_external(padR);