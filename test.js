
function equals(o1, o2){
    if(o1 == o2){
        return true;//same obj
    }else if(o1 instanceof Array){
        if(o2 instanceof Array && o1.length == o2.length){
            for(var i = 0; i < o1.length; i++){
                if(!equals(o1[i], o2[i])){
                    return false;
                }
            }
            return true;
        }
    }else if(typeof(o1) == 'object' && typeof(o2) == 'object' && Object.keys(o1).length == Object.keys(o2).length){
        for(var k in o1){
            if(!equals(o1[k], o2[k])){
                return false;
            }
        }
        return true;
    }
    return false;
}