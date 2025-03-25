
if(redis.call('get',KEYS[1]) == AVGS[1]) then
    return redis.call('del',KEYS[1])
end
return 0