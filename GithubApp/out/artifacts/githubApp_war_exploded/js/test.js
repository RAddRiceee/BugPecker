





router.post('/oauth', async function (ctx, next) {
    const { clientID = CLIENT_ID, clientSecret = CLIENT_SECRET, code } = ctx.request.body
    const { status, data } = await axios({
        method: 'post',
        url: 'http://github.com/login/oauth/access_token?' +
        `client_id=${clientID}&` +
        `client_secret=${clientSecret}&` +
        `code=${code}`,
        headers: {
            accept: 'application/json'
        }
    }).catch(e => e.response)
    ctx.body = { status, data }
})
