//#include <stdio.h>
#include <string.h>
#include <jni.h>
//#include "hyTypes.h"

#define BUFF_LENGTH		102400
jshort adcBuffer[BUFF_LENGTH];
int remainLen = 0;
//int dataLen = sizeof(adcData);
//256B*(8+2)bit*30*2B = 150KB 即最大封包为256个字节需要的adc buffer空间为150KB
/*-------------------------------------------------------------------------
* 函数:	audioInterface_wav2digital
* 说明:	分析mic的adc数据，解析出数字信号
* 参数:	x	-- in，wave 数据的PCM样本，jshort 类型
*		n	-- in, wave 数据PCM样本的个数， 输入大小和下位机协商，
*				保证一次最多输出一个条码数据
*		pdata -- out  解析出的数据的缓存buf
* 返回:	接收的数据的字节数，如果是多段通讯数据，则中间插入0，返回为值
		通讯的字节数+ (段数-1)
* ------------------------------------------------------------------------*/
int audioInterface_wav2digital(jshort *x, int n, jbyte *pdata)//记得添加波形数据拼接功能。
{
	int i,saveStartIndex = 0;
	jbyte ruler = 0,counterPos = 0,counterNeg = 0,counterTotal = 0,preFix = 0,suffix = 0,bitType = 0,bitMoveIndex = 0;
	jbyte dataIndex = 0,normalDir = 0,waveFreq = 0,startBit = 0,stopBit = 0,oldCounterNeg = 0,downTremble = 0;
	jbyte data[256],decodeDataLen = 0;
	memset(data,0,sizeof(data));
	if (remainLen > 0)//暂未考虑越界处理
	{
		memcpy(&adcBuffer[remainLen],x,n*sizeof(jshort));
		n += remainLen;
		remainLen = 0;
	}
	else
	{
		memcpy(adcBuffer,x,n*sizeof(jshort));
	}
	if (x == NULL || pdata == NULL )
	{
		return 0;
	}
	//关于上升沿和下降沿出现的抖动现象，低电平突变和高电平突变都有发生过，需要综合考虑。
	for (i = 0 ;i < n;i++)
	{
		if (ruler == 0)//初始化状态
				ruler = 1;//开始找电平起始位
		if (counterNeg > 100)
		{
			i++;
			i--;
		}
		if (adcBuffer[i] > 0)
		{
			//tmpData = ((jshort *)pAdcData)[i];


			if (ruler == 2)//低电平结束
			{
				if ((counterNeg > 5 && counterNeg < 9) || (counterNeg > 2 && counterNeg < 6) || (counterNeg > 7 && counterNeg < 18))//则说明是4/2sKHZ频率的低电平
				{
					if (counterNeg < 5 && waveFreq == 2)
					{
						ruler = 4;//认为是2KHZ低电平区域点抖动 需要将异变的高电平点归入到低电平区域
					}
					else
						ruler = 3;//4/2KHZ低电平
				}
				else if ((counterNeg < 3 ) && bitType > 0)//认为是下降沿抖动 低电平突变成高电平的情况
				{
					ruler = 4;//认为是低电平区域点抖动 需要将异变的高电平点归入到低电平区域
				}
				else
				{
					ruler = 0;//重新开始寻找电平起始位
					preFix = 0;
					counterPos = 0;
					counterNeg = 0;
					counterTotal = 0;
					suffix = 0;
					bitType = 0;
					bitMoveIndex = 0;
					saveStartIndex = 0;
					oldCounterNeg = 0;
					waveFreq = 0;
				}

			}
			else if (ruler == 5 && bitType >0)
			{
				if (counterNeg < 3)
				{
					counterPos += counterNeg;//将突变的负向点归入到正向点。
					counterNeg = oldCounterNeg;
					oldCounterNeg = 0;
					counterPos += 2;//跳过两个点，防范中间再有抖动。
					i+=2;
					ruler = 3;
				}
				else
				{
					ruler = 0;//重新开始寻找电平起始位
					preFix = 0;
					counterPos = 0;
					counterNeg = 0;
					counterTotal = 0;
					suffix = 0;
					bitType = 0;
					bitMoveIndex = 0;
					normalDir = 1;//表示不是反向，返回按正向波形处理
					saveStartIndex = 0;
					oldCounterNeg = 0;
					waveFreq = 0;
				}

			}
			else if (ruler == 6 && bitType == 1)
			{
				if ((counterNeg > 2 && counterNeg < 6 && waveFreq==1) || (counterNeg > 7 && counterNeg < 13 && waveFreq==2))//这里暂未考虑2/4KHZ波形互相异常突变到对方区域的情况
				{
					counterNeg = 0;
					ruler = 7;
					bitType = 2;
					dataIndex = 0;
					bitMoveIndex = 0;
					stopBit = 0;
					startBit = 0;
				}
				else
				{
					i = 0;
					ruler = 0;//重新开始寻找电平起始位
					preFix = 0;
					counterPos = 0;
					counterNeg = 0;
					counterTotal = 0;
					suffix = 0;
					bitType = 0;
					bitMoveIndex = 0;
					normalDir = 1;//表示不是反向，返回按正向波形处理
					saveStartIndex = 0;
					oldCounterNeg = 0;
					waveFreq = 0;
					continue;
				}
			}
			else if (ruler == 8)
			{
				if ((counterNeg > 5 && counterNeg < 9) || (counterNeg > 2 && counterNeg < 6) || (counterNeg > 7 && counterNeg < 17))//则说明是4/2KHZ频率高电平
				{

					counterTotal = counterPos + counterNeg;//计算的是前一个周期的点之和
					if ((counterTotal > 11 && counterTotal < 16) || (counterTotal > 23 && counterTotal < 31 ))//找到bit 1
					{
						if (bitType == 0)//理论上这里不会执行
						{
							bitType = 1;//前缀开始
							saveStartIndex = i;
						}
						if (bitType == 1)//前缀开始
						{
							preFix++;
						}
						else if (bitType == 2)//数据开始
						{
							if (waveFreq == 2 && counterTotal > 11 && counterTotal < 16 )
							{
								data[dataIndex] &= ~(0x01 << bitMoveIndex++);
							}
							else
							{
								data[dataIndex] |= (0x01 << bitMoveIndex++);
							}
							if (bitMoveIndex > 7)
							{
								dataIndex++;
								bitMoveIndex = 0;
								stopBit = 1;
							}
							if (suffix > 0)
							{
								if (++suffix > 8)//表示数据结束
								{
									dataIndex--;
									memcpy(&pdata[decodeDataLen],data,dataIndex);
									decodeDataLen += dataIndex;
									dataIndex = 0;
									ruler = 0;//重新开始寻找电平起始位
									preFix = 0;
									counterPos = 0;
									counterNeg = 0;
									counterTotal = 0;
									suffix = 0;
									bitType = 0;
									bitMoveIndex = 0;
									saveStartIndex = 0;
									oldCounterNeg = 0;
									waveFreq = 0;
								}
							}
						}
						ruler = 7;

					}
					else if ((counterTotal > 7 && counterTotal < 12 )|| (counterTotal > 15 && counterTotal < 24 ) )//找到bit 0
					{
						if (bitType == 1)
						{
							if (preFix > 4)//前缀结束
							{
								bitType = 2;//数据位开始
								dataIndex = 0;
								bitMoveIndex = 0;
								stopBit = 0;
								startBit = 0;
							}
						}
						else if (bitType == 2)
						{
							if (bitMoveIndex == 0)
							{
								suffix = 1;
							}
							else
							{
								suffix = 0;
							}

							if (startBit == 1 )//过滤数据起始和停止位
							{
								startBit = 0;
							}
							else if (stopBit == 1)
							{
								stopBit = 0;
								startBit = 1;
							}
							else
							{
								data[dataIndex] &= ~(0x01 << bitMoveIndex++);
								if (bitMoveIndex > 7)
								{
									dataIndex++;
									bitMoveIndex = 0;
									stopBit = 1;
								}
							}
						}
						ruler = 7;
					}
					else
					{
						memset(data,0,sizeof(data));
						ruler = 0;//重新开始寻找电平起始位
						preFix = 0;
						counterPos = 0;
						counterNeg = 0;
						counterTotal = 0;
						suffix = 0;
						bitType = 0;
						bitMoveIndex = 0;
						dataIndex = 0;
						saveStartIndex = 0;
						oldCounterNeg = 0;
						waveFreq = 0;
					}

				}
				else if (counterPos < 3 && bitType == 2)
				{
					ruler = 5;//认为是高电平区域点抖动 需要将异变的低电平点归入到高电平区域
				}
				else
				{
					memset(data,0,sizeof(data));
					ruler = 0;//重新开始寻找电平起始位
					preFix = 0;
					counterPos = 0;
					counterNeg = 0;
					counterTotal = 0;
					suffix = 0;
					bitType = 0;
					bitMoveIndex = 0;
					dataIndex = 0;
					saveStartIndex = 0;
					oldCounterNeg = 0;
					waveFreq = 0;
				}
				counterPos = 0;
				counterNeg = 0;
			}
			counterPos++;
		}
		else
		{
			if (ruler == 3)//高电平结束
			{
				if (bitType == 1 && preFix > 4) //前缀里出现0的上半周期时进行正反向判断
				{
					if (((counterPos > 2 && counterPos < 6) && (counterNeg > 5 && counterNeg < 9) && (counterNeg - counterPos > 0))|| //4KHZ
						((counterPos > 7 && counterPos < 13) && (counterNeg > 10 && counterNeg < 17) && (counterNeg - counterPos > 2)))//2KHZ
					{
						if (normalDir == 0)
							ruler = 6;//则说明是反向波形
					}
				}
				if (ruler != 6)
				{
					if ((counterPos > 5 && counterPos < 9) || (counterPos > 2 && counterPos < 6) || (counterPos > 7 && counterPos < 18))//则说明是4/2KHZ频率高电平 上升沿多次抖动会出现17的情况
					{
						if (i + 2 < n)//确保不越界 这里是对下降沿抖动，高电平突变成低电平的判断及处理 低电平突变成高电平的情况在上方if逻辑的代码中处理
						{
							if (adcBuffer[i+1] > 0 && bitType > 0 && counterNeg - counterPos > 1)//下降沿抖动 将抖动的低电平归入高电平
							{
								if (adcBuffer[i+2] > 0)//针对两个点抖动情况
								{
									counterPos+=3;
									i+=2;
								}
								else//针对一个点抖动情况
								{
									counterPos+=2;
									i++;
								}

								downTremble = 1;
								ruler = 2;
							}
						}

						counterTotal = counterPos + counterNeg;//计算的是前一个周期的点之和
						if ((counterTotal > 11 && counterTotal < 16 ) || (counterTotal > 23 && counterTotal < 31 ))//找到bit 1 下降沿抖动导致存在30的情况
						{
							if (bitType == 0)
							{
								bitType = 1;//前缀开始
								saveStartIndex = i;
								if (counterTotal > 11 && counterTotal < 16)
								{
									waveFreq = 1;//4KHZ
								}
								else if (counterTotal > 24 && counterTotal < 31)
								{
									waveFreq = 2;//2KHZ
								}
							}
							if (bitType == 1)//前缀开始
							{
								preFix++;
							}
							else if (bitType == 2)//数据开始
							{
								if (waveFreq == 2 && counterTotal > 11 && counterTotal < 16 )
								{
									data[dataIndex] &= ~(0x01 << bitMoveIndex++);
								}
								else
								{
									data[dataIndex] |= (0x01 << bitMoveIndex++);
								}

								if (bitMoveIndex > 7)
								{
									dataIndex++;
									bitMoveIndex = 0;
									stopBit = 1;
								}
								if (suffix > 0)
								{
									if (++suffix > 8)//表示数据结束
									{
										dataIndex--;
										memcpy(&pdata[decodeDataLen],data,dataIndex);
										decodeDataLen += dataIndex;
										dataIndex = 0;
										ruler = 0;//重新开始寻找电平起始位
										preFix = 0;
										counterPos = 0;
										counterNeg = 0;
										counterTotal = 0;
										suffix = 0;
										bitType = 0;
										bitMoveIndex = 0;
										saveStartIndex = 0;
										oldCounterNeg = 0;
										waveFreq = 0;
									}
								}
							}
							ruler = 2;

						}
						else if ((counterTotal > 7 && counterTotal < 12 )|| (counterTotal > 15 && counterTotal < 24) )//找到bit 0
						{
							if (bitType == 1)
							{
								if (preFix > 8)//前缀结束
								{
									bitType = 2;//数据位开始
									dataIndex = 0;
									bitMoveIndex = 0;
									stopBit = 0;
									startBit = 0;

								}
							}
							else if (bitType == 2)
							{
								if (bitMoveIndex == 0)
								{
									suffix = 1;
								}
								else
								{
									suffix = 0;
								}
								if (startBit == 1 )//过滤数据起始和停止位
								{
									startBit = 0;
								}
								else if (stopBit == 1)
								{
									stopBit = 0;
									startBit = 1;
								}
								else
								{
									data[dataIndex] &= ~(0x01 << bitMoveIndex++);
									if (bitMoveIndex > 7)
									{
										dataIndex++;
										bitMoveIndex = 0;
										stopBit = 1;
										if (dataIndex == 17)
										{
											i++;
											i--;
										}
									}
								}

							}
							ruler = 2;
						}
						else
						{
							memset(data,0,sizeof(data));
							ruler = 0;//重新开始寻找电平起始位
							preFix = 0;
							counterPos = 0;
							counterNeg = 0;
							counterTotal = 0;
							suffix = 0;
							bitType = 0;
							bitMoveIndex = 0;
							dataIndex = 0;
							saveStartIndex = 0;
							oldCounterNeg = 0;
							waveFreq = 0;
						}
						
					}
					else if (counterPos < 3 && bitType > 0)
					{
						ruler = 5;//认为是高电平区域点抖动 需要将异变的低电平点归入到高电平区域
						oldCounterNeg = counterNeg;
					}
					else
					{
						memset(data,0,sizeof(data));
						ruler = 0;//重新开始寻找电平起始位
						preFix = 0;
						counterPos = 0;
						counterNeg = 0;
						counterTotal = 0;
						suffix = 0;
						bitType = 0;
						bitMoveIndex = 0;
						dataIndex = 0;
						saveStartIndex = 0;
						oldCounterNeg = 0;
						waveFreq = 0;
					}
				}
				if (ruler != 5)
				{
					counterPos = 0;

				}
				counterNeg = 0;

			}
			else if (ruler == 4 && bitType > 0)//下降沿抖动 负向点突变成正向点
			{
				if (counterPos < 4)
				{
					counterNeg+= counterPos;//将突变的正向点归入到负向点
					counterPos = 0;
					if (i + 3 < n && waveFreq == 2)//预防二次抖动
					{
						counterNeg += 3;
						i += 3;
					}
					ruler = 2;
				}
				else
				{
					memset(data,0,sizeof(data));
					ruler = 0;//重新开始寻找电平起始位
					preFix = 0;
					counterPos = 0;
					counterNeg = 0;
					counterTotal = 0;
					suffix = 0;
					bitType = 0;
					bitMoveIndex = 0;
					dataIndex = 0;
					saveStartIndex = 0;
					oldCounterNeg = 0;
					waveFreq = 0;
				}
			}
			else if (ruler == 7)//反向低电平结束
			{
				if ((counterPos > 4 && counterPos < 9) || (counterPos > 2 && counterPos < 7) || (counterPos > 7 && counterPos < 17))//则说明是4/2sKHZ频率的低电平
				{
					//counterNeg = 0;
					ruler = 8;//4/2KHZ低电平
				}
				else if (counterPos < 3 && bitType == 2)//认为是抖动
				{
					ruler = 5;//认为是高电平区域点抖动 需要将异变的低电平点归入到高电平区域
				}
				else
				{
					memset(data,0,sizeof(data));
					ruler = 0;//重新开始寻找电平起始位
					preFix = 0;
					counterPos = 0;
					counterNeg = 0;
					counterTotal = 0;
					suffix = 0;
					bitType = 0;
					bitMoveIndex = 0;
					dataIndex = 0;
					saveStartIndex = 0;
					oldCounterNeg = 0;
					waveFreq = 0;
				}
			}
			if (downTremble == 1)
			{
				downTremble = 0;
			}
			else
			{
				counterNeg++;
			}

			if (ruler == 1)
			{
				if (counterPos > 0) //则认为是低电平起始
				{
					counterPos = 0;
					ruler = 2;//表示低电平起始

				}
			}
	
		}
	}
	if (saveStartIndex > 0)//因为前缀设置为2个字节的1，留有8bit1的冗余，这里少算1个bit位是允许的。
	{
		if (saveStartIndex > 100)
		{
			saveStartIndex -= 100;
		}
		else
		{
			saveStartIndex = 0;
		}
		//jshort tmpdata[5000];
		remainLen = n - saveStartIndex;
		if (remainLen<<1 < BUFF_LENGTH)
		{
			//memcpy(tmpdata,&adcBuffer[saveStartIndex],remainLen*sizeof(jshort));
			memcpy(adcBuffer,&adcBuffer[saveStartIndex],remainLen*sizeof(jshort));
		}
		else //暂不考虑其他情况
		{

		}
	}
	return decodeDataLen;
}
#if 0
/**************************************************************************//**
 * @brief  Main function
 *****************************************************************************/
int main(void)
{
	jbyte data[128];
	int i,k=0,dataLength = 0,tmpLen=1143;
	jshort tmpadcData[1000];
	memset(data,0,sizeof(data));
	tmpLen = sizeof(adcBuffer);
	//memset(adcBuffer,0,sizeof(adcBuffer));
	//memcpy(adcBuffer,adcData,sizeof(adcData));
	for (i = 0;i<dataLen;i+=200 )
	{
		memcpy(tmpadcData,&adcData[i*2],200*2);
		dataLength = audioInterface_wav2digital(tmpadcData,200,data);
		if (dataLength > 0)
		{
			k++;
		}

	}

	//tmpLen = dataLen - tmpLen;
	//dataLength = audioInterface_wav2digital((jshort *)adcData,dataLen,data);
	if (dataLength)
		dataLength = 0;
}
#endif
